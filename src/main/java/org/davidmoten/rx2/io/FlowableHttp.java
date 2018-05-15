package org.davidmoten.rx2.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.functions.Consumer;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.internal.util.BackpressureHelper;
import io.reactivex.plugins.RxJavaPlugins;

public final class FlowableHttp extends Flowable<ByteBuffer> {

    private final InputStream in;
    private final Consumer<Long> requester;
    private final int bufferSize;
    private final int preRequest;

    public FlowableHttp(InputStream in, Consumer<Long> requester, int preRequest, int bufferSize) {
        this.in = in;
        this.requester = requester;
        this.preRequest = preRequest;
        this.bufferSize = bufferSize;
    }

    @Override
    protected void subscribeActual(Subscriber<? super ByteBuffer> subscriber) {
        HttpSubscription subscription = new HttpSubscription(in, requester, preRequest, bufferSize, subscriber);
        subscription.start();
    }

    private static final class HttpSubscription extends AtomicLong implements Subscription {

        private static final long serialVersionUID = 5917186677331992560L;

        private final InputStream in;
        private final int bufferSize;
        private final Subscriber<? super ByteBuffer> child;

        private final int preRequest;
        private volatile boolean cancelled;
        private volatile Throwable error;
        private final Consumer<Long> requester;

        HttpSubscription(InputStream in, Consumer<Long> requester, int preRequest, int bufferSize,
                Subscriber<? super ByteBuffer> child) {
            this.in = in;
            this.requester = requester;
            this.preRequest = preRequest;
            this.bufferSize = bufferSize;
            this.child = child;
        }

        public void start() {
            child.onSubscribe(this);
            try {
                requester.accept((long) preRequest);
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                child.onError(e);
                return;
            }
        }

        @Override
        public void request(long n) {
            if (SubscriptionHelper.validate(n)) {
                try {
                    requester.accept(n);
                } catch (Throwable e) {
                    error = e;
                }
                if (BackpressureHelper.add(this, n) == 0) {
                    long r = get();
                    while (true) {
                        while (r > 0) {
                            if (tryCancelled()) {
                                return;
                            }
                            Throwable err = error;
                            if (err != null) {
                                error = null;
                                child.onError(err);
                                return;
                            }
                            // read some more
                            byte[] b = new byte[bufferSize];
                            try {
                                int count = in.read(b);
                                if (count == -1) {
                                    closeStream();
                                    child.onComplete();
                                    return;
                                } else {
                                    child.onNext(ByteBuffer.wrap(b, 0, count));
                                    r--;
                                }
                            } catch (Throwable e) {
                                Exceptions.throwIfFatal(e);
                                closeStream();
                                child.onError(e);
                                return;
                            }
                        }
                        r = addAndGet(-r);
                        if (r == 0) {
                            return;
                        }
                    }
                }
            }
        }

        private boolean tryCancelled() {
            if (cancelled) {
                closeStream();
                return true;
            } else {
                return false;
            }
        }

        private void closeStream() {
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
        }

    }

}
