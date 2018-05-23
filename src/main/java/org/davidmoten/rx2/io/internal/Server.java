package org.davidmoten.rx2.io.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.Scheduler.Worker;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.fuseable.SimplePlainQueue;
import io.reactivex.internal.queue.SpscLinkedArrayQueue;
import io.reactivex.plugins.RxJavaPlugins;

public final class Server {

    private Server() {
        // prevent instantiation
    }

    public static void handle(Flowable<ByteBuffer> flowable, Single<OutputStream> out,
            Runnable done, long id, Scheduler requestScheduler,
            Consumer<Subscription> subscription) {
        // when first request read (8 bytes) subscribe to Flowable
        // and output to OutputStream on scheduler
        HandlerSubscriber subscriber = new HandlerSubscriber(out, done, id, requestScheduler);
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
        private final Worker worker;
        private Subscription parent;
        private boolean done;
        private SimplePlainQueue<ByteBuffer> queue = new SpscLinkedArrayQueue<>(16);
        private volatile boolean finished;
        private Throwable error;
        private volatile boolean cancelled;
        private Disposable disposable;

        HandlerSubscriber(Single<OutputStream> outSource, Runnable completion, long id,
                Scheduler requestScheduler) {
            this.outSource = outSource;
            this.completion = completion;
            this.id = id;
            this.worker = requestScheduler.createWorker();
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
            try {
                out.write(Util.toBytes(id));
                out.flush();
            } catch (IOException e) {
                error = e;
                finished = true;
            }
            drain();
        }

        @Override
        public void request(long n) {
            worker.schedule(() -> {
                parent.request(n);
                drain();
            });
        }

        @Override
        public void cancel() {
            try {
                cancelled = true;
                disposable.dispose();
                parent.cancel();
                worker.dispose();
            } finally {
                Util.close(out);
            }
            completion.run();
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
                            worker.dispose();
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
                                worker.dispose();
                                if (!cancelled) {
                                    writeError(e);
                                }
                                return;
                            }
                        } else if (d) {
                            Throwable err = error;
                            if (err != null) {
                                error = null;
                                parent.cancel();
                                queue.clear();
                                worker.dispose();
                                writeError(err);
                                completion.run();
                                return;
                            } else {
                                doOnComplete();
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

        private void doOnComplete() {
            try {
                // send the bytes -128, 0, 0, 0 to indicate completion
                writeInt(out, Integer.MIN_VALUE);
            } catch (IOException e) {
                RxJavaPlugins.onError(e);
            } finally {
                Util.close(out);
            }
        }

        private void writeError(Throwable err) {
            try {
                // set initial size to cover size of most stack traces
                NoCopyByteArrayOutputStream bytes = new NoCopyByteArrayOutputStream(4096);
                err.printStackTrace(new PrintStream(bytes, true, "UTF-8"));
                bytes.close();

                // mark as error by reporting length as negative
                writeInt(out, -bytes.size());
                bytes.write(out);
                out.flush();
            } catch (IOException e) {
                RxJavaPlugins.onError(e);
            } finally {
                Util.close(out);
            }
        }

        private void writeOnNext(ByteBuffer b) throws IOException {
            writeInt(out, b.remaining());
            out.write(Util.toBytes(b));
            out.flush();
        }
    }

    private static void writeInt(OutputStream out, int v) throws IOException {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 0) & 0xFF);
    }

}
