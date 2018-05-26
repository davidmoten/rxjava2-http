package org.davidmoten.rx2.io.internal;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.FlowableSubscriber;
import io.reactivex.SingleObserver;
import io.reactivex.SingleSource;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.internal.subscriptions.SubscriptionHelper;

public final class FlowableSingleFlatMapPublisher<S, T> extends Flowable<T> {

    private final SingleSource<S> source;
    private final Function<? super S, ? extends Publisher<? extends T>> mapper;

    public FlowableSingleFlatMapPublisher(SingleSource<S> source,
            Function<? super S, ? extends Flowable<? extends T>> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    protected void subscribeActual(Subscriber<? super T> child) {
        SingleFlatMapPublisherObserver<S, T> observer = new SingleFlatMapPublisherObserver<S, T>(child, mapper);
        source.subscribe(observer);
    }

    static final class SingleFlatMapPublisherObserver<S, T> extends AtomicLong
            implements SingleObserver<S>, FlowableSubscriber<T>, Subscription {

        private static final long serialVersionUID = 7759721921468635667L;

        private final Subscriber<? super T> child;
        private final Function<? super S, ? extends Publisher<? extends T>> mapper;
        private Disposable disposable;
        private final AtomicReference<Subscription> parent;

        SingleFlatMapPublisherObserver(Subscriber<? super T> child,
                Function<? super S, ? extends Publisher<? extends T>> mapper) {
            this.child = child;
            this.mapper = mapper;
            this.parent = new AtomicReference<>();
        }

        @Override
        public void onSubscribe(Disposable d) {
            this.disposable = d;
            child.onSubscribe(this);
        }

        @Override
        public void onSuccess(S value) {
            Publisher<? extends T> f;
            try {
                f = mapper.apply(value);
            } catch (Exception e) {
                child.onError(e);
                return;
            }
            f.subscribe(this);
        }

        @Override
        public void onSubscribe(Subscription s) {
            SubscriptionHelper.deferredSetOnce(parent, this, s);
        }

        @Override
        public void onNext(T t) {
            child.onNext(t);
        }

        @Override
        public void onComplete() {
            child.onComplete();
        }

        @Override
        public void onError(Throwable e) {
            child.onError(e);
        }

        @Override
        public void request(long n) {
            SubscriptionHelper.deferredRequest(parent, this, n);
        }

        @Override
        public void cancel() {
            disposable.dispose();
            SubscriptionHelper.cancel(parent);
        }
    }

}
