package org.davidmoten.rx2.io.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.davidmoten.rx2.http.WriterFactory;
import org.junit.Assert;
import org.junit.Test;
import org.reactivestreams.Subscription;

import com.github.davidmoten.junit.Asserts;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class ServerTest {

    @Test
    public void isUtilityClass() {
        Asserts.assertIsUtilityClass(Server.class);
    }

    @Test
    public void handleOutputStreamThrowsWritingId() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException();
            }
        };
        Consumer<Subscription> consumer = sub -> {
        };
        Server.handle(Flowable.just(ByteBuffer.wrap(new byte[] { 1 })), Single.just(out), () -> {
        }, 123, Schedulers.trampoline(), consumer, WriterFactory.DEFAULT,
                AfterOnNextFactory.DEFAULT); //
    }

    @Test
    public void handleSubscriptionConsumerThrows() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {

            }
        };
        Consumer<Subscription> consumer = sub -> {
            throw new RuntimeException();
        };
        try {
            Server.handle(Flowable.just(ByteBuffer.wrap(new byte[] { 1 })), Single.just(out),
                    () -> {
                    }, 123, Schedulers.trampoline(), consumer, WriterFactory.DEFAULT,
                    AfterOnNextFactory.DEFAULT); //
        } catch (RuntimeException e) {
            Assert.assertEquals("subscription consumer threw", e.getMessage());
        }
    }

    @Test
    public void handleCancelWhileOutputting() {
        AtomicReference<Subscription> subscription = new AtomicReference<>();
        AtomicBoolean moreArrived = new AtomicBoolean(false);
        OutputStream out = new OutputStream() {
            int count;

            @Override
            public void write(int b) throws IOException {
                count++;
                if (count > 8) {
                    subscription.get().cancel();
                }
                if (count > 9) {
                    moreArrived.set(true);
                }

            }
        };
        Consumer<Subscription> consumer = sub -> {
            subscription.set(sub);
        };
        Server.handle(Flowable.just(ByteBuffer.wrap(new byte[] { 1 })), Single.just(out), () -> {
        }, 123, Schedulers.trampoline(), consumer, WriterFactory.DEFAULT,
                AfterOnNextFactory.DEFAULT); //
        subscription.get().request(100);
    }
}
