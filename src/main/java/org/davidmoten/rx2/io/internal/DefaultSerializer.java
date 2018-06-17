package org.davidmoten.rx2.io.internal;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.SerializerDeserializer;

import com.github.davidmoten.guavamini.annotations.VisibleForTesting;

public final class DefaultSerializer<T extends Serializable> implements SerializerDeserializer<T> {

    private final int bufferSize;

    private static final DefaultSerializer<Serializable> instance = new DefaultSerializer<>(128);

    @SuppressWarnings("unchecked")
    public static final <T extends Serializable> DefaultSerializer<T> instance() {
        return (DefaultSerializer<T>) instance;
    }

    public DefaultSerializer(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    @Override
    public ByteBuffer serialize(T t) {
        NoCopyByteArrayOutputStream bytes = new NoCopyByteArrayOutputStream(bufferSize);
        serialize(t, bytes);
        return bytes.asByteBuffer();
    }

    @VisibleForTesting
    static <T> void serialize(T t, OutputStream bytes) {
        try (ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
            oos.writeObject(t);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public T deserialize(ByteBuffer bb) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteBufferInputStream(bb))) {
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

}
