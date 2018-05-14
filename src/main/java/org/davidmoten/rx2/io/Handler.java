package org.davidmoten.rx2.io;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.internal.fuseable.SimplePlainQueue;
import io.reactivex.internal.queue.SpscLinkedArrayQueue;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.plugins.RxJavaPlugins;

public final class Handler {

    public static void handle(Flowable<ByteBuffer> f, InputStream in, OutputStream out) {
        handle(f, in, out, () -> {
        });
    }

    public static void handle(Flowable<ByteBuffer> f, InputStream in, OutputStream out,
            Runnable completion) {
        // when first request read (8 bytes) subscribe to Flowable
        // and output to OutputStream on scheduler

        HandlerSubscriber subscriber = new HandlerSubscriber(in, out, completion);
        f.subscribe(subscriber);

    }

    private static final class HandlerSubscriber extends AtomicInteger
            implements Subscriber<ByteBuffer> {

        private static final int REQUEST_CANCEL = -1;

        private static final long serialVersionUID = 1331107616659478552L;

        private final OutputStream out;
        private final WritableByteChannel channel;
        private final DataInputStream in;
        private Subscription parent;
        private boolean done;
        private SimplePlainQueue<ByteBuffer> queue = new SpscLinkedArrayQueue<>(16);
        private volatile boolean finished;
        private Throwable error;

        private volatile boolean cancelled;

        private final Runnable completion;

        public HandlerSubscriber(InputStream in, OutputStream out, Runnable completion) {
            this.in = new DataInputStream(in);
            this.out = out;
            this.channel = Channels.newChannel(out);
            this.completion = completion;
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.parent = s;
            while (true) {
                try {
                    /// blocking call
                    long request = in.readLong();
                    if (request == REQUEST_CANCEL) {
                        cancelled = true;
                    } else if (SubscriptionHelper.validate(request)) {
                        parent.request(request);
                    }
                } catch (EOFException e) {
                    // just means there will be no more requests
                    break;
                } catch (Throwable e) {
                    onError(e);
                    return;
                }
                drain();
            }
        }

        @Override
        public void onNext(ByteBuffer bb) {
            queue.offer(bb);
            drain();
        }

        @Override
        public void onError(Throwable e) {
            if (done) {
                RxJavaPlugins.onError(e);
                return;
            }
            done = true;
            error = e;
            finished = true;
            drain();
        }

        @Override
        public void onComplete() {
            if (done) {
                return;
            }
            done = true;
            finished = true;
            drain();
        }

        private void drain() {
            if (getAndIncrement() == 0) {
                int missed = 1;
                while (true) {
                    while (true) {
                        if (cancelled) {
                            parent.cancel();
                            queue.clear();
                            error = null;
                            completion.run();
                            return;
                        }
                        boolean d = finished;
                        ByteBuffer b = queue.poll();
                        if (b != null) {
                            try {
                                writeOnNext(b);
                            } catch (Throwable e) {
                                parent.cancel();
                                queue.clear();
                                writeError(e);
                                return;
                            }
                        } else if (d) {
                            Throwable err = error;
                            if (err != null) {
                                error = null;
                                parent.cancel();
                                queue.clear();
                                writeError(err);
                                completion.run();
                                return;
                            } else {
                                parent.cancel();
                                queue.clear();
                                try {
                                    out.close();
                                } catch (IOException e) {
                                    RxJavaPlugins.onError(e);
                                }
                                completion.run();
                                return;
                            }
                        } else {
                            break;
                        }
                    }
                    missed = addAndGet(-missed);
                    if (missed == 0) {
                        return;
                    }
                }
            }
        }

        private void writeError(Throwable err) {
            try {
                NoCopyByteArrayOutputStream bytes = new NoCopyByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bytes);
                oos.writeObject(err);
                oos.close();
                System.out.println("sourceErrorBytes=" + bytes.size());
                // mark as error by reporting length as negative
                writeInt(out, -bytes.size());
                channel.write(bytes.asByteBuffer());
                channel.close();
                out.close();
            } catch (IOException e) {
                RxJavaPlugins.onError(e);
            }
        }

        private void writeOnNext(ByteBuffer b) throws IOException {
            writeInt(out, b.remaining());
            channel.write(b);
        }
    }

    private static void writeInt(OutputStream out, int v) throws IOException {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 0) & 0xFF);
    }

    public static void handleBlocking(Flowable<ByteBuffer> f, ServletInputStream in,
            ServletOutputStream out) {
        CountDownLatch latch = new CountDownLatch(1);
        handle(f, in, out, () -> latch.countDown());
        try {
            latch.await();
        } catch (InterruptedException e) {
            // do nothing
        }
    }
}
