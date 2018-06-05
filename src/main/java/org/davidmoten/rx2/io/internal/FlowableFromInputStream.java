package org.davidmoten.rx2.io.internal;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.functions.BiConsumer;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.internal.util.EmptyComponent;
import io.reactivex.plugins.RxJavaPlugins;

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

    private static final class FromStreamSubscription extends AtomicInteger implements Subscription {

        private static final long serialVersionUID = 5917186677331992560L;

        private static final long ID_UNKNOWN = 0;

        private final InputStream in;
        private final Subscriber<? super ByteBuffer> child;
        private final AtomicReference<IdRequested> requested;

        private volatile boolean cancelled;
        private final BiConsumer<Long, Long> requester;
        private int length = 0;
        private byte[] buffer;
        private int bufferIndex;

        private static final class IdRequested {
            final long id;
            final long requested;

            IdRequested(long id, long requested) {
                this.id = id;
                this.requested = requested;
            }
        }

        FromStreamSubscription(InputStream in, BiConsumer<Long, Long> requester, Subscriber<? super ByteBuffer> child) {
            this.in = in;
            this.requester = requester;
            this.child = child;
            this.requested = new AtomicReference<>(new IdRequested(0, 0));
        }

        public void start() {
            child.onSubscribe(this);
            long id;
            try {
                id = Util.readLong(in);
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                closeStreamSilently();
                child.onSubscribe(EmptyComponent.INSTANCE);
                child.onError(e);
                return;
            }
            while (true) {
                IdRequested idr = requested.get();
                if (requested.compareAndSet(idr, new IdRequested(id, idr.requested))) {
                    try {
                        requester.accept(id, idr.requested);
                    } catch (Exception e) {
                        Exceptions.throwIfFatal(e);
                        closeStreamSilently();
                        child.onError(e);
                        return;
                    }
                    break;
                }
            }
            drain();
        }

        @Override
        public void request(long n) {
            if (SubscriptionHelper.validate(n)) {
                while (true) {
                    IdRequested idr = requested.get();
                    if (idr.requested == Long.MAX_VALUE) {
                        break;
                    }
                    long r2 = idr.requested + n;
                    if (r2 < 0) {
                        r2 = Long.MAX_VALUE;
                    }
                    if (requested.compareAndSet(idr, new IdRequested(idr.id, r2))) {
                        if (idr.id != ID_UNKNOWN) {
                            try {
                                requester.accept(idr.id, idr.requested);
                            } catch (Exception e) {
                                Exceptions.throwIfFatal(e);
                                closeStreamSilently();
                                // TODO use finished and error fields
                                child.onError(e);
                                return;
                            }
                            break;
                        }
                    }
                }
                drain();
            }
        }

        private void drain() {
            if (getAndIncrement() == 0) {
                int missed = 1;
                while (true) {
                    IdRequested idr = requested.get();
                    long r = idr.requested;
                    long e = 0;
                    while (e != r) {
                        if (tryCancelled()) {
                            return;
                        }
                        // read some more
                        if (buffer == null) {
                            try {
                                length = Util.readInt(in);
                            } catch (IOException e1) {
                                emitError(e1);
                                return;
                            }
                            if (length == Integer.MIN_VALUE) {
                                System.out.println("complete");
                                closeStreamSilently();
                                child.onComplete();
                                return;
                            }
                            buffer = new byte[Math.abs(length)];
                            bufferIndex = 0;
                        }
                        try {
                            int count = in.read(buffer, bufferIndex, Math.abs(length) - bufferIndex);
                            if (count == -1) {
                                emitError(new EOFException("encountered EOF before expected length was read"));
                                return;
                            }
                            bufferIndex += count;
                            if (bufferIndex == Math.abs(length)) {
                                if (length < 0) {
                                    String t = new String(buffer, 0, -length, StandardCharsets.UTF_8);
                                    buffer = null;
                                    child.onError(new RuntimeException(t));
                                    return;
                                } else {
                                    child.onNext(ByteBuffer.wrap(buffer, 0, length));
                                    buffer = null;
                                    e++;
                                }
                            }
                        } catch (Throwable ex) {
                            emitError(ex);
                            return;
                        }
                    }
                    if (e != 0) {
                        while (true) {
                            idr = requested.get();
                            long r2 = idr.requested - e;
                            if (r2 < 0L) {
                                RxJavaPlugins.onError(new IllegalStateException("More produced than requested: " + r2));
                                r2 = 0;
                            }
                            if (requested.compareAndSet(idr, new IdRequested(idr.id, r2))) {
                                break;
                            }
                        }
                    }
                    missed = addAndGet(-missed);
                    if (missed == 0) {
                        return;
                    }
                }
            }
        }

        private void emitError(Throwable e) {
            closeStreamSilently();
            child.onError(e);
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
            IdRequested idr = requested.get();
            if (idr.id != ID_UNKNOWN) {
                try {
                    // a negative request cancels the stream
                    requester.accept(idr.id, -1L);
                } catch (Exception e) {
                    Exceptions.throwIfFatal(e);
                    closeStreamSilently();
                    RxJavaPlugins.onError(e);
                    return;
                }
            }
        }

    }

}
