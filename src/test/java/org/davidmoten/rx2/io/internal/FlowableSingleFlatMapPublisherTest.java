package org.davidmoten.rx2.io.internal;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

import io.reactivex.Flowable;
import io.reactivex.Single;

public class FlowableSingleFlatMapPublisherTest {

    @Test
    public void testSimple() {
        new FlowableSingleFlatMapPublisher<>(Single.just(1), n -> Flowable.just(1, 2)) //
                .test() //
                .assertValues(1, 2) //
                .assertComplete();
    }

    @Test
    public void testDeferred() {
        new FlowableSingleFlatMapPublisher<>(Single.timer(500, TimeUnit.MILLISECONDS), n -> Flowable.just(1, 2)) //
                .test() //
                .awaitDone(2, TimeUnit.SECONDS) //
                .assertValues(1, 2) //
                .assertComplete();
    }

}
