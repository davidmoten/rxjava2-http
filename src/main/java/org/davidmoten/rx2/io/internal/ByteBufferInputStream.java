package org.davidmoten.rx2.io.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public final class ByteBufferInputStream extends InputStream {

    private final ByteBuffer bb;

    public ByteBufferInputStream(ByteBuffer bb) {
        this.bb = bb;
    }

    @Override
    public int read() throws IOException {
        if (!bb.hasRemaining()) {
            return -1;
        }
        return bb.get() & 0xFF;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        if (length == 0) {
            return 0;
        }
        int n = Math.min(bb.remaining(), length);
        if (n == 0) {
            return -1;
        }
        bb.get(bytes, offset, n);
        return n;
    }

    @Override
    public int available() throws IOException {
        return bb.remaining();
    }
}