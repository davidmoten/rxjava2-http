package org.davidmoten.rx2.io.internal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.github.davidmoten.junit.Asserts;

public class UtilTest {
    
    @Test
    public void isUtilityClass() {
        Asserts.assertIsUtilityClass(Util.class);
    }
    
    @Test
    public void testToLong() {
        assertEquals(12345L, Util.toLong(Util.toBytes(12345L)));
    }

}
