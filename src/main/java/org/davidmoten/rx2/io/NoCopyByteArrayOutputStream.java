package org.davidmoten.rx2.io;

import java.io.ByteArrayOutputStream;

public final class NoCopyByteArrayOutputStream extends ByteArrayOutputStream{

    public byte[] arrayInternal() {
        return super.buf;
    }
    
}
