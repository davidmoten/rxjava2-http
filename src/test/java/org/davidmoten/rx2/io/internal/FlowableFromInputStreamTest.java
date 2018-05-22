package org.davidmoten.rx2.io.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.junit.Test;

public class FlowableFromInputStreamTest {

    @Test
    public void testFirstReadFails() {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {});
        new FlowableFromInputStream(in, (id, r) -> {}) //
          .test() //
          .assertNoValues() //
          .assertError(IOException.class);
    }
    
    @Test
    public void testRequesterFails() {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[] { 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 1, 12, -128, 0, 0, 0 });
        new FlowableFromInputStream(in, (id, r) -> {throw new RuntimeException("boo");}) //
          .test(1) //
          .assertValueCount(1) //
          .assertErrorMessage("boo");
    }

}
