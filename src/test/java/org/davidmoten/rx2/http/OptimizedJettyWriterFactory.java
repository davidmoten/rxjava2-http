package org.davidmoten.rx2.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.eclipse.jetty.server.HttpOutput;

public final class OptimizedJettyWriterFactory implements WriterFactory {

    public static final OptimizedJettyWriterFactory INSTANCE = new OptimizedJettyWriterFactory();

    @Override
    public Writer createWriter(OutputStream out) {
        if (out instanceof HttpOutput) {
            return createWriter((HttpOutput) out);
        } else {
            return Writer.createDefault(out);
        }
    }

    private static Writer createWriter(HttpOutput out) {
        return new Writer() {

            @Override
            public void write(ByteBuffer bb) throws IOException {
                out.write(bb);
            }

            @Override
            public void write(int b) throws IOException {
                out.write(b);
            }
            
            @Override
            public void write(byte[] buffer, int offset, int length) throws IOException {
                out.write(buffer, offset, length);
            }

            @Override
            public void flush() throws IOException {
                out.flush();
            }

            @Override
            public void close() throws IOException {
                out.close();
            }
            
        };
    }

}
