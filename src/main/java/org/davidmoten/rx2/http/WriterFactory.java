package org.davidmoten.rx2.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.internal.Util;

public interface WriterFactory {

    Writer createWriter(OutputStream out);

    public static final WriterFactory DEFAULT = new WriterFactory() {

        @Override
        public Writer createWriter(OutputStream out) {
            return Writer.createDefault(out);
        }
    };

    public static WriterFactory flushAfterItems(int numItems) {
        return flushAfter(numItems, 0);
    }

    public static WriterFactory flushAfterBytes(int numBytes) {
        return flushAfter(0, numBytes);
    }

    public static WriterFactory flushAfter(int numItems, int numBytes) {
        return new WriterFactory() {

            @Override
            public Writer createWriter(OutputStream out) {
                return new Writer() {

                    int count;
                    int countBytes;

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
                    public void close() throws IOException {
                        out.close();
                    }

                    @Override
                    public void afterOnNext(int numBytes) throws IOException {
                        count++;
                        countBytes += numBytes;
                        boolean flush = false;
                        if (numItems > 0) {
                            if (count >= numItems) {
                                flush = true;
                            }
                        }
                        if (numBytes > 0) {
                            if (countBytes >= numBytes) {
                                flush = true;
                            }
                        }
                        if (flush) {
                            count = 0;
                            countBytes = 0;
                            flush();
                        }
                    }
                };
            }

        };
    }

}
