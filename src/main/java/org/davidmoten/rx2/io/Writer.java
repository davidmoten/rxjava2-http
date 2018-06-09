package org.davidmoten.rx2.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.internal.Util;

public interface Writer {

    public void write(ByteBuffer bb) throws IOException;

    public static Writer createDefault(OutputStream out) {
        return new Writer() {

            @Override
            public void write(ByteBuffer bb) throws IOException {
                out.write(Util.toBytes(bb));
            }
        };
    }

}
