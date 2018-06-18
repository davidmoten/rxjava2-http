package org.davidmoten.rx2.io;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;

public class ClientMain {

    private static final Logger log = LoggerFactory.getLogger(ClientMain.class);

    public static void main(String[] args) {
        long N = Long.MAX_VALUE;
        Flowable<Long> f = Flowable.defer(() -> {
            AtomicLong count = new AtomicLong();
            return Client.get("http://localhost:" + 8080 + "/") //
                    .<Long>deserializer(bb -> bb.getLong()) //
                    .rebatchRequests(1024) //
                    .doOnNext(n -> {
                        long c = count.incrementAndGet();
                        if (c % 100000 == 0) {
                            log.info("count={}", c);
                        }
                        assertEquals((long) n, c);
                    }) //
                    .take(N);
        });
        f.count() //
                .test() //
                .awaitDone(Long.MAX_VALUE, TimeUnit.DAYS) //
                .assertValue(N) //
                .assertComplete();
    }
}
