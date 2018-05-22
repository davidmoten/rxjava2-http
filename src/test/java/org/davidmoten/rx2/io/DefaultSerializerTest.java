package org.davidmoten.rx2.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.junit.Test;

public class DefaultSerializerTest {

    @Test(expected = RuntimeException.class)
    public void testSerializeThrows() {
        DefaultSerializer.serialize(1L, new OutputStream() {

            @Override
            public void write(int b) throws IOException {
                throw new IOException("boo");
            }
        });
    }

    @Test(expected = RuntimeException.class)
    public void testDeserializeThrows() {
        DefaultSerializer.instance().deserialize(ByteBuffer.wrap(new byte[] {}));
    }

}
