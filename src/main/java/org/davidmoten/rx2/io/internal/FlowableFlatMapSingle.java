package org.davidmoten.rx2.io.internal;

import java.util.concurrent.atomic.AtomicReference;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;

public final class FlowableFlatMapSingle<S, T> extends Flowable<T> {

    private final Single<S> source;
    private final Function<? super S, ? extends Flowable<T>> mapper;

    public FlowableFlatMapSingle(Single<S> source,
            Function<? super S, ? extends Flowable<T>> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    protected void subscribeActual(Subscriber<? super T> child) {
        FlatMapSingleObserver<S, T> subscriber = new FlatMapSingleObserver<S, T>(child, mapper);
        source.subscribe(subscriber);
    }

    static final class FlatMapSingleObserver<S, T>
            implements SingleObserver<S>, Subscriber<T>, Subscription {

        private final Subscriber<? super T> child;
        private final Function<? super S, ? extends Flowable<T>> mapper;
        private Disposable disposable;
        private final AtomicReference<Subscription> parent = new AtomicReference<>();

        FlatMapSingleObserver(Subscriber<? super T> child,
                Function<? super S, ? extends Flowable<T>> mapper) {
            this.child = child;
            this.mapper = mapper;
        }

        @Override
        public void onSubscribe(Disposable d) {
            this.disposable = d;
            child.onSubscribe(this);
        }

        @Override
        public void onSuccess(S value) {
            Flowable<T> f;
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
            parent.compareAndSet(null, s);
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
            parent.request(n);
        }

        @Override
        public void cancel() {
            parent.cancel();
        }
    }

}
