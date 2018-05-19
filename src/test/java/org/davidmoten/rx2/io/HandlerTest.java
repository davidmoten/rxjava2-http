package org.davidmoten.rx2.io;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.davidmoten.rx2.io.internal.Util;
import org.junit.Test;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;

public class HandlerTest {

    private static final Runnable DO_NOTHING = () -> {
    };

    @Test
    public void testOneByteStream() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Flowable<ByteBuffer> f = Flowable.just(ByteBuffer.wrap(new byte[] { 12 }));
        AtomicReference<Subscription> subscription = new AtomicReference<Subscription>();
        Consumer<Subscription> consumer = sub -> subscription.set(sub);
        Handler.handle(f, Single.just(out), DO_NOTHING, 2, consumer);
        subscription.get().request(1);
        System.out.println(Arrays.toString(out.toByteArray()));
        assertArrayEquals(new byte[] { 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 1, 12 }, // id=2,length=1,byte=12
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
        Handler.handle(f, Single.just(out), DO_NOTHING, id, consumer);
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.write(Util.toBytes(id));
        expected.write(Util.toBytes(-exBytes.length));
        expected.write(exBytes);
        assertArrayEquals(expected.toByteArray(), out.toByteArray());
    }

    private static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
            oos.writeObject(o);
        }
        return bytes.toByteArray();
    }
}
