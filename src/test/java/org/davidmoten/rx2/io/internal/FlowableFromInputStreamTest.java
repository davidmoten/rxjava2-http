package org.davidmoten.rx2.io.internal;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;

public class FlowableFromInputStreamTest {

    @Test
    public void testFirstReadFails() {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {});
        new FlowableFromInputStream(in, (id, r) -> {
        }) //
                .test() //
                .assertNoValues() //
                .assertError(IOException.class);
    }

    @Test
    public void testRequesterFails() {
        ByteArrayInputStream in = new ByteArrayInputStream(
                new byte[] { 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 1, 12, -128, 0, 0, 0 });
        new FlowableFromInputStream(in, (id, r) -> {
            throw new RuntimeException("boo");
        }) //
                .test(1) //
                .assertValueCount(1) //
                .assertErrorMessage("boo");
    }

    @Test
    public void testInputStreamThrowsImmediately() {
        InputStream in = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("always throw");
            }
        };
        new FlowableFromInputStream(in, (id, r) -> {
        }) //
                .test() //
                .assertNoValues() //
                .assertError(IOException.class);
    }

    @Test
    public void testInputStreamThrowsAfterIdRead() {
        InputStream in = new InputStream() {
            int count;

            @Override
            public int read() throws IOException {
                count++;
                if (count > 8) {
                    throw new IOException("always throw");
                } else {
                    return 1;
                }
            }
        };
        new FlowableFromInputStream(in, (id, r) -> {
        }) //
                .test() //
                .assertNoValues() //
                .assertError(IOException.class);
    }

    @Test
    public void testInputStreamThrowsAfterLengthRead() {
        InputStream in = new InputStream() {
            int count;
            final byte[] bytes = new byte[] { 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 3 };

            @Override
            public int read() throws IOException {
                count++;
                if (count > bytes.length) {
                    throw new IOException("always throw");
                } else {
                    return bytes[count - 1];
                }
            }
        };
        new FlowableFromInputStream(in, (id, r) -> {
        }) //
                .test() //
                .assertNoValues() //
                .assertError(IOException.class);
    }

    @Test
    public void testInputStreamEndsWhileReadingMessage() {
        InputStream in = new InputStream() {
            int count;
            // first 8 bytes are id=1
            // next 4 is length = 3
            final byte[] bytes = new byte[] { 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 3 };

            @Override
            public int read() throws IOException {
                count++;
                if (count > bytes.length) {
                    return -1;
                } else {
                    return bytes[count - 1];
                }
            }
        };
        new FlowableFromInputStream(in, (id, r) -> {
        }) //
                .test() //
                .assertNoValues() //
                .assertError(EOFException.class);
    }

    @Test
    public void testCloseNullDoesNotThrow() {
        FlowableFromInputStream.close(null);
    }

    @Test
    public void testCloseThrowingDoesNotThrow() {
        FlowableFromInputStream.close(new Closeable() {

            @Override
            public void close() throws IOException {
                throw new IOException();
            }
        });
    }

    @Test
    public void testCloseNotThrowing() {
        FlowableFromInputStream.close(new Closeable() {

            @Override
            public void close() throws IOException {
                // do nothing
            }
        });
    }

}
