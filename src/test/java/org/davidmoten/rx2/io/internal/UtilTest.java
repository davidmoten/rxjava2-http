package org.davidmoten.rx2.io.internal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UtilTest {
    
    @Test
    public void testToLong() {
        assertEquals(12345L, Util.toLong(Util.toBytes(12345L)));
    }

}
