package org.davidmoten.rx2.io;

import java.io.OutputStream;

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
        return bb -> out.write(bb);
    }

}
