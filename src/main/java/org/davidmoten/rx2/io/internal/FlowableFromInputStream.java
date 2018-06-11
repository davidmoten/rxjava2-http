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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.davidmoten.guavamini.annotations.VisibleForTesting;

import io.reactivex.Flowable;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.functions.BiConsumer;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.plugins.RxJavaPlugins;

public final class FlowableFromInputStream extends Flowable<ByteBuffer> {

    private static final Logger log = LoggerFactory.getLogger(FlowableFromInputStream.class);
    private final InputStream in;
    private final BiConsumer<Long, Long> requester;

    public FlowableFromInputStream(InputStream in, BiConsumer<Long, Long> requester) {
        this.in = in;
        this.requester = requester;
    }

    @Override
    protected void subscribeActual(Subscriber<? super ByteBuffer> subscriber) {
        log.debug("subscribeActual");
        FromStreamSubscription subscription = new FromStreamSubscription(in, requester, subscriber);
        subscription.start();
    }

    @VisibleForTesting
    static final class FromStreamSubscription extends AtomicInteger implements Subscription {

        private static final Logger log = LoggerFactory.getLogger(FromStreamSubscription.class);

        private static final long serialVersionUID = 5917186677331992560L;

        private static final long ID_UNKNOWN = 0;

        private final InputStream in;
        private final Subscriber<? super ByteBuffer> child;
        private final AtomicReference<IdRequested> requested;

        private final BiConsumer<Long, Long> requester;
        private int length = 0;
        private byte[] buffer;
        private int bufferIndex;
        private volatile Throwable error;
        private static final IdRequested HAVE_NOT_READ_ID = new IdRequested(0, 0);

        FromStreamSubscription(InputStream in, BiConsumer<Long, Long> requester,
                Subscriber<? super ByteBuffer> child) {
            this.in = in;
            this.requester = requester;
            this.child = child;
            this.requested = new AtomicReference<>(HAVE_NOT_READ_ID);
        }

        void start() {
            log.debug("calling child.onSubscribe");
            child.onSubscribe(this);
        }

        private void readId(long n) {
            log.debug("reading id");
            long id;
            try {
                id = Util.readLong(in);
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                closeStreamSilently();
                error = e;
                return;
            }
            log.debug("id={}", id);
            while (true) {
                IdRequested idr = requested.get();
                if (idr == null) {
                    // cancel occured while reading id
                    // we must endeavour to cancel the server side of the connection explicitly
                    cancelUpstream(id);
                    break;
                }
                if (requested.compareAndSet(idr, new IdRequested(id, n))) {
                    try {
                        log.debug("requesting id={}, n={}", idr.id, n);
                        requester.accept(id, n);
                    } catch (Exception e) {
                        Exceptions.throwIfFatal(e);
                        closeStreamSilently();
                        error = e;
                        break;
                    }
                    break;
                }
            }
        }

        @Override
        public void request(long n) {
            log.debug("request {}", n);
            if (SubscriptionHelper.validate(n)) {
                if (requested.compareAndSet(HAVE_NOT_READ_ID, new IdRequested(0, 0))) {
                    // watch out for concurrent calls to request at this point, should be handled ok
                    readId(n);
                    drain();
                    return;
                }
                while (true) {
                    IdRequested idr = requested.get();
                    if (idr == null) {
                        // cancelled
                        break;
                    }
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
                                log.debug("requesting {} from stream {}", idr.requested, n);
                                requester.accept(idr.id, n);
                            } catch (Exception e) {
                                Exceptions.throwIfFatal(e);
                                error = e;
                                break;
                            }
                        }
                        break;
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
                    long r;
                    if (idr == null) {
                        r = 0;
                    } else {
                        r = idr.requested;
                    }
                    long e = 0;
                    if (r == 0) {
                        // need to cover off not being able to get the id or make an initial request
                        Throwable err = error;
                        if (err != null) {
                            error = null;
                            emitError(err);
                            return;
                        }
                    }
                    while (e != r) {
                        if (tryCancelled()) {
                            return;
                        }
                        // read some more
                        Throwable err = error;
                        if (buffer == null) {
                            if (err != null) {
                                error = null;
                                emitError(err);
                                return;
                            } else {
                                try {
                                    length = Util.readInt(in);
                                } catch (IOException ex) {
                                    emitError(ex);
                                    return;
                                }
                                if (length == Integer.MIN_VALUE) {
                                    closeStreamSilently();
                                    child.onComplete();
                                    return;
                                }
                                buffer = new byte[Math.abs(length)];
                                bufferIndex = 0;
                            }
                        }
                        try {
                            int count = in.read(buffer, bufferIndex,
                                    Math.abs(length) - bufferIndex);
                            if (count == -1) {
                                emitError(new EOFException(
                                        "encountered EOF before expected length was read"));
                                return;
                            }
                            bufferIndex += count;
                            if (bufferIndex == Math.abs(length)) {
                                if (length < 0) {
                                    String t = new String(buffer, 0, -length,
                                            StandardCharsets.UTF_8);
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
                        produced(requested, e);
                    }
                    missed = addAndGet(-missed);
                    if (missed == 0) {
                        return;
                    }
                }
            }
        }

        private void emitError(Throwable e) {
            if (!cancelled()) {
                closeStreamSilently();
                System.out.println("emitting error " + e.getMessage());
                child.onError(e);
            }
        }

        private boolean tryCancelled() {
            return requested.get() == null;
        }

        private void closeStreamSilently() {
            Util.close(in);
        }

        private boolean cancelled() {
            return requested.get() == null;
        }

        @Override
        public void cancel() {
            while (true) {
                IdRequested idr = requested.get();
                if (idr == null) {
                    // already cancelled
                    break;
                }
                if (requested.compareAndSet(idr, null)) {
                    if (idr.id != ID_UNKNOWN) {
                        cancelUpstream(idr.id);
                    }
                    break;
                }
            }
        }

        private void cancelUpstream(long id) {
            log.debug("cancelUpstream");
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

    @VisibleForTesting
    static void produced(AtomicReference<IdRequested> requested, long e) {
        IdRequested idr;
        while (true) {
            idr = requested.get();
            if (idr == null) {
                // have been cancelled
                return;
            }
            long r2 = idr.requested - e;
            if (r2 < 0L) {
                RxJavaPlugins
                        .onError(new IllegalStateException("More produced than requested: " + r2));
                r2 = 0;
            }
            if (requested.compareAndSet(idr, new IdRequested(idr.id, r2))) {
                break;
            }
        }
    }

    @VisibleForTesting
    static final class IdRequested {
        final long id;
        final long requested;

        IdRequested(long id, long requested) {
            this.id = id;
            this.requested = requested;
        }
    }

}
