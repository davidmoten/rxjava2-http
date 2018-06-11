package org.davidmoten.rx2.http;

import java.io.OutputStream;

public interface WriterFactory {

    Writer createWriter(OutputStream out);

    public static final WriterFactory DEFAULT = new WriterFactory() {

        @Override
        public Writer createWriter(OutputStream out) {
            return Writer.createDefault(out);
        }
    };

}
