package org.davidmoten.rx2.io.internal;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.Scheduler.Worker;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.functions.BiConsumer;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.internal.util.BackpressureHelper;
import io.reactivex.internal.util.EmptyComponent;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;

public final class FlowableFromInputStream extends Flowable<ByteBuffer> {

    private final InputStream in;
    private final BiConsumer<Long, Long> requester;

    public FlowableFromInputStream(InputStream in, BiConsumer<Long, Long> requester) {
        this.in = in;
        this.requester = requester;
    }

    @Override
    protected void subscribeActual(Subscriber<? super ByteBuffer> subscriber) {
        FromStreamSubscription subscription = new FromStreamSubscription(in, requester, subscriber);
        subscription.start();
    }

    private static final class FromStreamSubscription extends AtomicInteger
            implements Subscription, Runnable {

        private static final long serialVersionUID = 5917186677331992560L;

        private Throwable error;
        private volatile boolean finished;
        private final InputStream in;
        private final Subscriber<? super ByteBuffer> child;
        private final AtomicLong requested;
        private long id;

        private volatile boolean cancelled;
        private final BiConsumer<Long, Long> requester;
        private long emitted;
        private int length = 0;
        private byte[] buffer;
        private int bufferIndex;
        private final Worker worker;
        private boolean reading;
        private boolean idRead;

        // the next emission
        private ByteBuffer next;

        FromStreamSubscription(InputStream in, BiConsumer<Long, Long> requester,
                Subscriber<? super ByteBuffer> child) {
            this.in = in;
            this.requester = requester;
            this.child = child;
            this.requested = new AtomicLong();
            this.worker = Schedulers.io().createWorker();
        }

        public void start() {
            child.onSubscribe(this);
            drain();
        }

        @Override
        public void request(long n) {
            if (SubscriptionHelper.validate(n)) {
                BackpressureHelper.add(requested, n);
                try {
                    requester.accept(id, n);
                } catch (Exception e) {
                    Exceptions.throwIfFatal(e);
                    closeStreamSilently();
                    child.onError(e);
                    return;
                }
                drain();
            }
        }

        @Override
        public void run() {
            if (!idRead) {
                try {
                    id = Util.readLong(in);
                    System.out.println("read id");
                } catch (Throwable e) {
                    Exceptions.throwIfFatal(e);
                    closeStreamSilently();
                    child.onSubscribe(EmptyComponent.INSTANCE);
                    child.onError(e);
                    return;
                }
                idRead = true;
            }
            if (buffer == null) {
                try {
                    System.out.println("reading item length");
                    length = Util.readInt(in);
                    System.out.println("read item length " + length);
                } catch (IOException ex) {
                    error = ex;
                    finished = true;
                    drain();
                    return;
                }
                if (length == Integer.MIN_VALUE) {
                    System.out.println("complete");
                    closeStreamSilently();
                    finished = true;
                    drain();
                    return;
                }
                buffer = new byte[Math.abs(length)];
                bufferIndex = 0;
            }
            try {
                int count = in.read(buffer, bufferIndex, Math.abs(length) - bufferIndex);
                if (count == -1) {
                    error = new EOFException("encountered EOF before expected length was read");
                    finished = true;
                    drain();
                    return;
                }
                bufferIndex += count;
                if (bufferIndex == Math.abs(length)) {
                    if (length < 0) {
                        String t = new String(buffer, 0, -length, StandardCharsets.UTF_8);
                        buffer = null;
                        error = new RuntimeException(t);
                        finished = true;
                        drain();
                        return;
                    } else {
                        next = ByteBuffer.wrap(buffer, 0, length);
                        buffer = null;
                        reading = false;
                        drain();
                        return;
                    }
                }
            } catch (Throwable ex) {
                error = ex;
                finished = true;
                drain();
                return;
            }
        }

        private void drain() {
            if (getAndIncrement() == 0) {
                int missed = 1;
                while (true) {
                    if (tryCancelled()) {
                        return;
                    }
                    if (!reading) {
                        long r = requested.get();
                        long e = emitted;
                        while (e != r) {
                            System.out.println("in loop");
                            boolean d = finished;
                            ByteBuffer bb = next;
                            if (bb != null) {
                                next = null;
                                child.onNext(bb);
                                e++;
                            } else {
                                if (d) {
                                    Throwable err = error;
                                    if (err != null) {
                                        closeStreamSilently();
                                        child.onError(err);
                                        return;
                                    } else {
                                        closeStreamSilently();
                                        child.onComplete();
                                        return;
                                    }
                                } else {
                                    reading = true;
                                    worker.schedule(this);
                                    break;
                                }
                            }
                            if (tryCancelled()) {
                                return;
                            }
                        }
                        emitted = e;
                    }
                    missed = addAndGet(-missed);
                    if (missed == 0) {
                        return;
                    }
                }
            }
        }

        private boolean tryCancelled() {
            if (cancelled) {
                closeStreamSilently();
                return true;
            } else {
                return false;
            }
        }

        private void closeStreamSilently() {
            Util.close(in);
        }

        @Override
        public void cancel() {
            cancelled = true;
            worker.dispose();
            try {
                // a negative request cancels the stream
                requester.accept(id, -1L);
            } catch (Exception e) {
                Exceptions.throwIfFatal(e);
                RxJavaPlugins.onError(e);
                return;
            } finally {
                closeStreamSilently();
            }
        }

    }

}
