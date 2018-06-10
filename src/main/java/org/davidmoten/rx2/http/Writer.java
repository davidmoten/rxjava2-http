package org.davidmoten.rx2.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.internal.Util;

public interface Writer {

    public void write(ByteBuffer bb) throws IOException;

    public void write(int b) throws IOException;

    public void flush() throws IOException;

    public void close() throws IOException;

    public default void write(byte[] buffer, int offset, int length) throws IOException {
        int to = offset + length;
        for (int i = offset; i < to; i++) {
            write(buffer[i]);
        }
    }

    public default void write(byte[] bytes) throws IOException {
        write(bytes, 0, bytes.length);
    }

    public static Writer createDefault(OutputStream out) {
        return new Writer() {

            @Override
            public void write(ByteBuffer bb) throws IOException {
                out.write(Util.toBytes(bb));
            }

            @Override
            public void write(int b) throws IOException {
                out.write(b);
            }

            @Override
            public void flush() throws IOException {
                out.flush();
            }

            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                out.write(bytes, offset, length);
            }

            @Override
            public void close() throws IOException {
                out.close();
            }
        };
    }

}
