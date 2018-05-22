package org.davidmoten.rx2.io;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.internal.ByteBufferInputStream;
import org.davidmoten.rx2.io.internal.NoCopyByteArrayOutputStream;

import com.github.davidmoten.guavamini.annotations.VisibleForTesting;

public final class DefaultSerializer<T extends Serializable> implements Serializer<T> {

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
        return serialize(t, bytes);
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
    
    @VisibleForTesting
    static <T> ByteBuffer serialize(T t, NoCopyByteArrayOutputStream bytes) {
        try (ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
            oos.writeObject(t);
            return bytes.asByteBuffer();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
