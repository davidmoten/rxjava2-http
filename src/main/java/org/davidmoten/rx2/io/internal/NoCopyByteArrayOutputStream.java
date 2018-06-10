package org.davidmoten.rx2.io.internal;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public final class NoCopyByteArrayOutputStream extends ByteArrayOutputStream {

    public NoCopyByteArrayOutputStream(int size) {
        super(size);
    }

    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(buf, 0, size());
    }
    
    public byte[] buffer() {
        return buf;
    }

}
