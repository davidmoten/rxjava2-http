package org.davidmoten.rx2.io;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.internal.fuseable.SimplePlainQueue;
import io.reactivex.internal.queue.SpscLinkedArrayQueue;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.internal.util.BackpressureHelper;

public final class FlowableSplitBytes extends Flowable<ByteBuffer> {

    private final Flowable<? extends ByteBuffer> source;
    private final byte[] delimiter;

    public FlowableSplitBytes(Flowable<? extends ByteBuffer> source, byte[] delimiter) {
        this.source = source;
        this.delimiter = delimiter;
    }

    @Override
    protected void subscribeActual(Subscriber<? super ByteBuffer> child) {
        SplitBytesSubscriber subscriber = new SplitBytesSubscriber(child, delimiter);
        source.subscribe(subscriber);
    }

    private static final class SplitBytesSubscriber extends AtomicInteger
            implements Subscriber<ByteBuffer>, Subscription {

        private static final long serialVersionUID = 3564048429094095025L;

        private final Subscriber<? super ByteBuffer> child;
        private Subscription parent;
        private SimplePlainQueue<ByteBuffer> queue = new SpscLinkedArrayQueue<>(16);
        private volatile boolean finished;
        private Throwable error;
        private volatile boolean cancelled;
        private final AtomicLong requested = new AtomicLong();
        private long emitted;
        private volatile List<ByteBuffer> list = new LinkedList<>();

        private final byte[] delimiter;

        public SplitBytesSubscriber(Subscriber<? super ByteBuffer> child, byte[] delimiter) {
            this.child = child;
            this.delimiter = delimiter;
        }

        @Override
        public void onSubscribe(Subscription parent) {
            this.parent = parent;
            child.onSubscribe(this);
        }

        @Override
        public void onNext(ByteBuffer bb) {
            list.add(bb);
            ByteBuffer b;
            while ((b = find()) != null) {
                queue.offer(b);
            }
            drain();
        }

        private ByteBuffer find() {
            return null;
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

        @Override
        public void request(long n) {
            if (SubscriptionHelper.validate(n)) {
                BackpressureHelper.add(requested, n);
                drain();
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        private void drain() {
            if (getAndIncrement() == 0) {
                int missed = 1;
                while (true) {
                    if (tryCancelled()) {
                        return;
                    }
                    long r = requested.get();
                    long e = emitted;
                    while (e != r) {
                        boolean d = finished;
                        ByteBuffer bb = queue.poll();
                        if (bb == null) {
                            Throwable err = error;
                            if (err != null) {
                                error = null;
                                child.onError(err);
                                e++;
                            } else {
                                child.onComplete();
                            }
                            return;
                        } else {
                            child.onNext(bb);
                            e++;
                        }
                        if (tryCancelled()) {
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
                queue.clear();
                error = null;
                return true;
            } else {
                return false;
            }
        }

    }

}
