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

    private static long toLong(byte[] b) {
        return (((long) b[0] << 56) //
                + ((long) (b[1] & 255) << 48) //
                + ((long) (b[2] & 255) << 40) //
                + ((long) (b[3] & 255) << 32) //
                + ((long) (b[4] & 255) << 24) //
                + ((b[5] & 255) << 16) //
                + ((b[6] & 255) << 8) //
                + ((b[7] & 255) << 0));
    }

    private static byte[] toBytes(long v) {
        byte[] b = new byte[8];
        b[0] = (byte) (v >>> 56);
        b[1] = (byte) (v >>> 48);
        b[2] = (byte) (v >>> 40);
        b[3] = (byte) (v >>> 32);
        b[4] = (byte) (v >>> 24);
        b[5] = (byte) (v >>> 16);
        b[6] = (byte) (v >>> 8);
        b[7] = (byte) (v >>> 0);
        return b;
    }

    public byte[] toBytes(int v) throws IOException {
        byte[] b = new byte[4];
        b[0] = (byte) ((v >>> 24) & 0xFF);
        b[1] = (byte) ((v >>> 16) & 0xFF);
        b[2] = (byte) ((v >>> 8) & 0xFF);
        b[3] = (byte) ((v >>> 0) & 0xFF);
        return b;
    }
}
