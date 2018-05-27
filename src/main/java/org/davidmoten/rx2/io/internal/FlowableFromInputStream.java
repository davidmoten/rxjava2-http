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
        FromStreamSubscription subscription = new FromStreamSubscription(in, requester, subscriber);
        subscription.start();
    }

    private static final class FromStreamSubscription extends AtomicInteger implements Subscription {

        private static final long serialVersionUID = 5917186677331992560L;

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

        FromStreamSubscription(InputStream in, BiConsumer<Long, Long> requester, Subscriber<? super ByteBuffer> child) {
            this.in = in;
            this.requester = requester;
            this.child = child;
            this.requested = new AtomicLong();
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
                    emitted = e;
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
            try {
                // a negative request cancels the stream
                requester.accept(id, -1L);
            } catch (Exception e) {
                Exceptions.throwIfFatal(e);
                closeStreamSilently();
                RxJavaPlugins.onError(e);
                return;
            }
        }

    }

}
