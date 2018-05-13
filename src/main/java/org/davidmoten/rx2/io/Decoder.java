package org.davidmoten.rx2.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Callable;

import io.reactivex.Emitter;
import io.reactivex.Flowable;
import io.reactivex.functions.Consumer;

public final class Decoder {

    private Decoder() {
        // prevent instantiation
    }

    public static Flowable<ByteBuffer> decode(Callable<InputStream> in, int bufferSize) {
        return Flowable.defer(() -> {
            DataInputStream d = new DataInputStream(in.call());
            return Flowable.generate(new Consumer<Emitter<ByteBuffer>>() {
                byte[] buffer = new byte[bufferSize];
                boolean first = true;
                boolean isError;
                final ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();

                @Override
                public void accept(Emitter<ByteBuffer> emitter) throws Exception {
                    if (first) {
                        int length = d.readInt();
                        if (length < 0) {
                            isError = true;
                            length = -length;
                        }
                        first = false;
                    }
                    int n = d.read(buffer);
                    if (n == -1) {
                        if (isError) {
                            Throwable ex = deserialize(errorBytes.toByteArray());
                            emitter.onError(ex);
                        } else {
                            emitter.onComplete();
                        }
                    } else {
                        ByteBuffer bb = ByteBuffer.wrap(Arrays.copyOf(buffer, n));
                        emitter.onNext(bb);
                    }
                }
            });
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserialize(byte[] bytes) throws ClassNotFoundException, IOException {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (T) ois.readObject();
        }
    }
}
