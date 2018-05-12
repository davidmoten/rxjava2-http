package org.davidmoten.rx2.io;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.atomic.AtomicInteger;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.plugins.RxJavaPlugins;

public final class Handler {

    public static void handle(Flowable<ByteBuffer> f, InputStream in, OutputStream out,
            Scheduler scheduler) {
        // when first request read (8 bytes) subscribe to Flowable
        // and output to OutputStream on scheduler

        HandlerSubscriber subscriber = new HandlerSubscriber(in, out);
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
        private ByteBuffer bb;
        private volatile boolean finished;
        private Throwable error;

        private volatile boolean cancelled;

        public HandlerSubscriber(InputStream in, OutputStream out) {
            this.in = new DataInputStream(in);
            this.out = out;
            this.channel = Channels.newChannel(out);
        }

        @Override
        public void onSubscribe(Subscription s) {
            this.parent = s;
            while (true) {
                try {
                    long request = in.readLong();
                    if (request == REQUEST_CANCEL) {
                        cancelled = true;
                    } else {
                        parent.request(request);
                    }
                } catch (IOException e) {
                    onError(e);
                    return;
                }
            }
        }

        @Override
        public void onNext(ByteBuffer bb) {
            this.bb = bb;
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
                    if (cancelled) {
                        bb = null;
                        error = null;
                        return;
                    }
                    boolean d = finished;
                    ByteBuffer b = this.bb;
                    if (b != null) {
                        this.bb = null;
                        try {
                            writeOnNext(b);
                        } catch (IOException e) {
                            parent.cancel();
                            writeError(e);
                            return;
                        }
                    }
                    if (d) {
                        Throwable err = error;
                        if (err != null) {
                            error = null;
                            bb = null;
                            parent.cancel();
                            writeError(err);
                            return;
                        } else {
                            parent.cancel();
                            try {
                                out.close();
                            } catch (IOException e) {
                                RxJavaPlugins.onError(e);
                            }
                            return;
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
                byte[] b = bytes.arrayInternal();
                // mark as error by reporting length as negative
                writeInt(out, -b.length);
                channel.write(ByteBuffer.wrap(b));
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
}
