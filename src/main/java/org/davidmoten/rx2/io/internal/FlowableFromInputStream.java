package org.davidmoten.rx2.io.internal;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.functions.BiConsumer;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.internal.util.BackpressureHelper;
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
        FromStreamSubscriber subscription = new FromStreamSubscriber(in, requester, subscriber);
        subscription.start();
    }

    private static final class FromStreamSubscriber extends AtomicInteger implements Subscription {

        private static final long serialVersionUID = 5917186677331992560L;

        private final InputStream in;
        private final Subscriber<? super ByteBuffer> child;
        private final AtomicLong requested = new AtomicLong();
        private long id;

        private volatile boolean cancelled;
        private volatile Throwable error;
        private final BiConsumer<Long, Long> requester;
        private long emitted;
        private int length = 0;
        private byte[] buffer;
        private int bufferIndex;

        FromStreamSubscriber(InputStream in, BiConsumer<Long, Long> requester,
                Subscriber<? super ByteBuffer> child) {
            this.in = in;
            this.requester = requester;
            this.child = child;
        }

        public void start() {
            try {
                id = Util.readLong(in);
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                closeStreamSilently();
                child.onSubscribe(EmptyComponent.INSTANCE);
                child.onError(e);
                return;
            }
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

        private void drain() {
            if (getAndIncrement() == 0) {
                int missed = 1;
                while (true) {
                    long r = requested.get();
                    long e = emitted;
                    while (e != r) {
                        if (tryCancelled()) {
                            return;
                        }
                        Throwable err = error;
                        if (err != null) {
                            error = null;
                            closeStreamSilently();
                            child.onError(err);
                            return;
                        }
                        // read some more
                        if (buffer == null) {
                            try {
                                length = Util.readInt(in);
                            } catch (EOFException e1) {
                                closeStreamSilently();
                                child.onComplete();
                                return;
                            } catch (IOException e1) {
                                closeStreamSilently();
                                child.onError(e1);
                                return;
                            }
                            buffer = new byte[length];
                        }
                        try {
                            int count = in.read(buffer, bufferIndex, length - bufferIndex);
                            bufferIndex += count;
                            if (count == -1) {
                                closeStreamSilently();
                                child.onError(new EOFException(
                                        "encountered EOF before expected length was read"));
                                return;
                            } else if (bufferIndex == length) {
                                child.onNext(ByteBuffer.wrap(buffer, 0, length));
                                buffer = null;
                                e++;
                            }
                        } catch (Throwable ex) {
                            Exceptions.throwIfFatal(ex);
                            closeStreamSilently();
                            child.onError(ex);
                            return;
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

        private boolean tryCancelled() {
            if (cancelled) {
                closeStreamSilently();
                return true;
            } else {
                return false;
            }
        }

        private void closeStreamSilently() {
            close(in);
        }

        private void close(Closeable c) {
            if (c != null) {
                try {
                    c.close();
                } catch (IOException e) {
                    RxJavaPlugins.onError(e);
                }
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
            try {
                // a negative request cancels the stream
                requester.accept(id, -1L);
            } catch (Exception e) {
                Exceptions.throwIfFatal(e);
                closeStreamSilently();
                child.onError(e);
                return;
            }
        }

    }

}
