package org.davidmoten.rx2.io;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicInteger;

import org.davidmoten.rx2.io.internal.NoCopyByteArrayOutputStream;
import org.davidmoten.rx2.io.internal.Util;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.fuseable.SimplePlainQueue;
import io.reactivex.internal.queue.SpscLinkedArrayQueue;
import io.reactivex.plugins.RxJavaPlugins;

public final class Handler {

    /**
     * Subscribes to {@code out} to obtain an {@link OutputStream}. The first 8
     * bytes written to the {@code OutputStream} are the id of the stream.
     * 
     * @param flowable
     * @param out
     * @param completion
     * @param id
     * @param subscription
     */
    public static void handle(Flowable<ByteBuffer> flowable, Single<OutputStream> out,
            Runnable completion, long id, Consumer<Subscription> subscription) {
        // when first request read (8 bytes) subscribe to Flowable
        // and output to OutputStream on scheduler
        HandlerSubscriber subscriber = new HandlerSubscriber(out, completion, id);
        try {
            subscription.accept(subscriber);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        flowable.subscribe(subscriber);
    }
     
    private static final class HandlerSubscriber extends AtomicInteger
            implements Subscriber<ByteBuffer>, Subscription, SingleObserver<OutputStream> {

        private static final long serialVersionUID = 1331107616659478552L;

        private final Single<OutputStream> outSource;
        private OutputStream out;
        private final Runnable completion;
        private final long id;
        private WritableByteChannel channel;
        private Subscription parent;
        private boolean done;
        private SimplePlainQueue<ByteBuffer> queue = new SpscLinkedArrayQueue<>(16);
        private volatile boolean finished;
        private Throwable error;
        private volatile boolean cancelled;
        private Disposable disposable;

        HandlerSubscriber(Single<OutputStream> outSource, Runnable completion, long id) {
            this.outSource = outSource;
            this.completion = completion;
            this.id = id;
        }

        @Override
        public void onSubscribe(Subscription parent) {
            this.parent = parent;
            outSource.subscribe(this);
        }

        // SingleObserver for outSource

        @Override
        public void onSubscribe(Disposable d) {
            disposable = d;
        }

        @Override
        public void onSuccess(OutputStream os) {
            this.out = os;
            this.channel = Channels.newChannel(out);
            try {
                out.write(Util.toBytes(id));
            } catch (IOException e) {
                error = e;
                finished = true;
            }
            drain();
        }

        // end of SingleObserver

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

        @Override
        public void request(long n) {
            parent.request(n);
            drain();
        }

        @Override
        public void cancel() {
            disposable.dispose();
            parent.cancel();
            cancelled = true;
        }
    }

    private static void writeInt(OutputStream out, int v) throws IOException {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 0) & 0xFF);
    }

}
