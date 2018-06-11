package org.davidmoten.rx2.io.internal;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.davidmoten.rx2.http.WriterFactory;
import org.junit.Test;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class HandlerTest {

    private static final Runnable DO_NOTHING = () -> {
    };

    @Test
    public void testOneByteStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Flowable<ByteBuffer> f = Flowable.just(ByteBuffer.wrap(new byte[] { 12 }));
        AtomicReference<Subscription> subscription = new AtomicReference<Subscription>();
        Consumer<Subscription> consumer = sub -> subscription.set(sub);
        Server.handle(f, Single.just(out), DO_NOTHING, 2, Schedulers.trampoline(), consumer,
                WriterFactory.DEFAULT, AfterOnNextFactory.DEFAULT);
        subscription.get().request(1);
        System.out.println(Arrays.toString(out.toByteArray()));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 1, 12, -128, 0, 0, 0 }, // id=2,length=1,byte=12
                out.toByteArray());
    }

    @Test
    public void testErrorStream() throws IOException {
        RuntimeException ex = new RuntimeException("boo");
        byte[] exBytes = serialize(ex);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Flowable<ByteBuffer> f = Flowable.error(ex);
        AtomicReference<Subscription> subscription = new AtomicReference<Subscription>();
        Consumer<Subscription> consumer = sub -> subscription.set(sub);
        long id = 2;
        Server.handle(f, Single.just(out), DO_NOTHING, id, Schedulers.trampoline(), consumer,
                WriterFactory.DEFAULT, AfterOnNextFactory.DEFAULT);
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.write(Util.toBytes(id));
        expected.write(Util.toBytes(-exBytes.length));
        expected.write(exBytes);
        assertArrayEquals(expected.toByteArray(), out.toByteArray());
    }

    private static byte[] serialize(Throwable t) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bytes, true, "UTF-8");
        t.printStackTrace(out);
        return bytes.toByteArray();
    }
}
