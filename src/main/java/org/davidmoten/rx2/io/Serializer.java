package org.davidmoten.rx2.io;

import java.nio.ByteBuffer;

public interface Serializer<T> {

    ByteBuffer serialize(T t);
    
    T deserialize(ByteBuffer bb);
    
}
