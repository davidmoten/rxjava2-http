package org.davidmoten.rx2.io;

import java.io.Serializable;
import java.nio.ByteBuffer;

public interface Serializer<T> {

    ByteBuffer serialize(T t);
    
    T deserialize(ByteBuffer bb);
    
    public static <T extends Serializable> Serializer<T> javaIo() {
        return DefaultSerializer.<T>instance();
    }
    
}
