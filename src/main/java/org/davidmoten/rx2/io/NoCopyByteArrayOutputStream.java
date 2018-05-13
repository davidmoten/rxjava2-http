package org.davidmoten.rx2.io;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public final class NoCopyByteArrayOutputStream extends ByteArrayOutputStream{

    public ByteBuffer asByteBuffer() {
        return ByteBuffer.wrap(buf, 0, size());
    }
    
}
