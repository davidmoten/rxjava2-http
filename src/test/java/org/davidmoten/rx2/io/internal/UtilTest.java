package org.davidmoten.rx2.io.internal;

import static org.junit.Assert.assertEquals;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

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

    @Test
    public void testCloseNullDoesNotThrow() {
        Util.close(null);
    }

    @Test
    public void testCloseThrowingDoesNotThrow() {
        Util.close(new Closeable() {

            @Override
            public void close() throws IOException {
                throw new IOException();
            }
        });
    }

    @Test
    public void testCloseNotThrowing() {
        Util.close(new Closeable() {

            @Override
            public void close() throws IOException {
                // do nothing
            }
        });
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testReadFullyNegativeLengthShouldThrow() throws IOException {
        Util.readFully(new InputStream() {
            @Override
            public int read() throws IOException {
                return 0;
            }
        }//
                , new byte[] { 1, 1 }, 0, -5);
    }

    @Test(expected = EOFException.class)
    public void readIntEof() throws IOException {
        InputStream in = new InputStream() {

            @Override
            public int read() throws IOException {
                return -1;
            }
        };
        Util.readInt(in);
    }

}
