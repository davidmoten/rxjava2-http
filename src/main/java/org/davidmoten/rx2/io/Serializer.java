package org.davidmoten.rx2.io;

import java.io.Serializable;
import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.internal.DefaultSerializer;

public interface Serializer<T> {

    ByteBuffer serialize(T t);

    public static <T extends Serializable> SerializerDeserializer<T> javaIo() {
        return DefaultSerializer.<T>instance();
    }

}
