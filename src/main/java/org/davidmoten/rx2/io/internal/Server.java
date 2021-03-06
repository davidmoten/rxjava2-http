package org.davidmoten.rx2.io.internal;

import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.davidmoten.rx2.http.Writer;
import org.davidmoten.rx2.http.WriterFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.Scheduler;
import io.reactivex.Scheduler.Worker;
import io.reactivex.SingleObserver;
import io.reactivex.SingleSource;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.fuseable.SimplePlainQueue;
import io.reactivex.internal.queue.SpscLinkedArrayQueue;
import io.reactivex.internal.util.BackpressureHelper;
import io.reactivex.plugins.RxJavaPlugins;

public final class Server {

    private Server() {
        // prevent instantiation
    }

    public static void handle(Publisher<? extends ByteBuffer> flowable,
            SingleSource<OutputStream> out, Runnable completion, long id,
            Scheduler requestScheduler, Consumer<Subscription> subscription,
            WriterFactory writerFactory, AfterOnNextFactory afterOnNextFactory) {
        // when first request read (8 bytes) subscribe to Flowable
        // and output to OutputStream on scheduler
        HandlerSubscriber subscriber = new HandlerSubscriber(out, completion, id, requestScheduler,
                writerFactory, afterOnNextFactory.create());
        try {
            subscription.accept(subscriber);
        } catch (Exception e) {
            throw new RuntimeException("subscription consumer threw", e);
        }
        flowable.subscribe(subscriber);
    }

    private static final class HandlerSubscriber extends AtomicInteger
            implements Subscriber<ByteBuffer>, Subscription, SingleObserver<OutputStream> {

        private static final Logger log = LoggerFactory.getLogger(HandlerSubscriber.class);

        private static final long serialVersionUID = 1331107616659478552L;

        private final SingleSource<OutputStream> outSource;
        private final Runnable completion;
        private final long id;
        private final Worker worker;
        private final WriterFactory writerFactory;
        private final AfterOnNext afterOnNext;
        private Subscription parent;
        private SimplePlainQueue<ByteBuffer> queue;
        private volatile boolean finished;
        private Throwable error;
        private volatile boolean cancelled;
        private Disposable disposable;
        private Writer writer;
        private final AtomicLong requested = new AtomicLong();
        private long emitted;

        HandlerSubscriber(SingleSource<OutputStream> outSource, Runnable completion, long id,
                Scheduler requestScheduler, WriterFactory writerFactory, AfterOnNext afterOnNext) {
            this.outSource = outSource;
            this.completion = completion;
            this.id = id;
            this.writerFactory = writerFactory;
            this.worker = requestScheduler.createWorker();
            this.queue = new SpscLinkedArrayQueue<>(16);
            this.afterOnNext = afterOnNext;
        }

        @Override
        public void onSubscribe(Subscription parent) {
            this.parent = parent;
            outSource.subscribe(this);
            log.debug("subscribed to source");
        }

        // SingleObserver for outSource

        @Override
        public void onSubscribe(Disposable d) {
            disposable = d;
        }

        @Override
        public void onSuccess(OutputStream os) {
            try {
                writer = writerFactory.createWriter(os);
                writer.write(Util.toBytes(id));
                writer.flush();
            } catch (IOException e) {
                error = e;
                finished = true;
            }
            drain();
        }

        @Override
        public void request(long n) {
            log.debug("server request id={}, n={}", id, n);
            BackpressureHelper.add(requested, n);
            worker.schedule(() -> {
                parent.request(n);
                drain();
            });
        }

        @Override
        public void cancel() {
            cancelled = true;
            disposable.dispose();
            parent.cancel();
            worker.dispose();
        }

        // end of SingleObserver

        @Override
        public void onNext(ByteBuffer bb) {
            queue.offer(bb);
            drain();
        }

        @Override
        public void onError(Throwable e) {
            error = e;
            finished = true;
            drain();
        }

        @Override
        public void onComplete() {
            finished = true;
            drain();
        }

        private void drain() {
            if (getAndIncrement() == 0) {
                int missed = 1;
                while (true) {
                    long r = requested.get();
                    long e = emitted;
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
                        ByteBuffer bb = queue.poll();
                        if (bb != null) {
                            try {
                                e++;
                                writeOnNext(bb, e == r);
                            } catch (Throwable ex) {
                                parent.cancel();
                                queue.clear();
                                worker.dispose();
                                if (!cancelled) {
                                    writeError(ex);
                                }
                                completion.run();
                                return;
                            }
                        } else if (d) {
                            Throwable err = error;
                            if (err != null) {
                                error = null;
                                parent.cancel();
                                queue.clear();
                                worker.dispose();
                                if (!cancelled) {
                                    writeError(err);
                                }
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
                    emitted = e;
                    missed = addAndGet(-missed);
                    if (missed == 0) {
                        return;
                    }
                }
            }
        }

        private void doOnComplete() {
            log.debug("server: onComplete");
            try {
                // send the bytes -128, 0, 0, 0 to indicate completion
                writeInt(writer, Integer.MIN_VALUE);
                writer.flush();
            } catch (IOException e) {
                RxJavaPlugins.onError(e);
            }
        }

        private void writeError(Throwable err) {
            log.debug("server: onError", err);
            try {
                // set initial size to cover size of most stack traces
                NoCopyByteArrayOutputStream bytes = new NoCopyByteArrayOutputStream(4096);
                err.printStackTrace(new PrintStream(bytes, true, "UTF-8"));
                bytes.close();

                // mark as error by reporting length as negative
                writeInt(writer, -bytes.size());
                writer.write(bytes.buffer(), 0, bytes.size());
                writer.flush();
            } catch (IOException e) {
                // cancellation will close the OutputStream
                // so we won't report that
                if (!(e instanceof EOFException)) {
                    RxJavaPlugins.onError(e);
                }
            }
        }

        boolean firstOnNext = true;

        private void writeOnNext(ByteBuffer bb, boolean emittedEqualsRequested) throws IOException {
            if (firstOnNext) {
                log.debug("server: first onNext");
                firstOnNext = false;
            }
            writeInt(writer, bb.remaining());
            writer.write(bb);
            if (emittedEqualsRequested || afterOnNext.flushRequested(bb.remaining())) {
                writer.flush();
            }
        }

    }

    private static void writeInt(Writer writer, int v) throws IOException {
        writer.write((v >>> 24) & 0xFF);
        writer.write((v >>> 16) & 0xFF);
        writer.write((v >>> 8) & 0xFF);
        writer.write((v >>> 0) & 0xFF);
    }

}
