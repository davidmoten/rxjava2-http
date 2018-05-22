package org.davidmoten.rx2.io;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.internal.ByteBufferInputStream;
import org.junit.Test;

public class ByteBufferInputStreamTest {

    private static final ByteBufferInputStream EMPTY = new ByteBufferInputStream(ByteBuffer.wrap(new byte[] {}));

    @Test
    public void testAvailable() throws IOException {
        assertEquals(0, EMPTY.available());
    }

    @Test
    public void testEof() throws IOException {
        assertEquals(-1, EMPTY.read());
    }

    @Test
    public void testReadLengthZero() throws IOException {
        assertEquals(0, EMPTY.read(new byte[4], 0, 0));
    }

    @Test
    public void testReadInt() throws IOException {
        ByteBufferInputStream b = new ByteBufferInputStream(ByteBuffer.wrap(new byte[] { 12 }));
        assertEquals(12, b.read());
    }

    @Test
    public void testReadArrayEOF() throws IOException {
        assertEquals(-1, EMPTY.read(new byte[4], 0, 1));
    }
}
