package org.davidmoten.rx2.io;

import java.nio.ByteBuffer;

public interface Deserializer<T> {
    T deserialize(ByteBuffer bb);
}
