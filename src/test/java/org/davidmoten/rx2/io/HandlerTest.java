package org.davidmoten.rx2.io;

import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Test;

import io.reactivex.Flowable;

public class HandlerTest {

    @Test
    public void testOneByteStream() {
        InputStream in = new ByteArrayInputStream(toBytes(1L));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Flowable<ByteBuffer> f = Flowable.just(ByteBuffer.wrap(new byte[] { 12 }));
        Handler.handle(f, in, out);
        assertArrayEquals(new byte[] { 0, 0, 0, 1, 12 }, out.toByteArray());
    }

    @Test
    public void testErrorStream() throws IOException {
        RuntimeException ex = new RuntimeException("boo");
        byte[] exBytes = serialize(ex);
        InputStream in = new ByteArrayInputStream(toBytes(1L));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Flowable<ByteBuffer> f = Flowable.error(ex);
        Handler.handle(f, in, out);
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.write(toBytes(-exBytes.length));
        expected.write(exBytes);
        assertArrayEquals(Arrays.copyOf(expected.toByteArray(), 4),
                Arrays.copyOf(out.toByteArray(), 4));
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
