package org.davidmoten.rx2.io.internal;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import io.reactivex.plugins.RxJavaPlugins;

public class Util {

    private Util() {
        // prevent instantiation
    }

    public static long toLong(byte[] b) {
        return (((long) b[0] << 56) //
                + ((long) (b[1] & 255) << 48) //
                + ((long) (b[2] & 255) << 40) //
                + ((long) (b[3] & 255) << 32) //
                + ((long) (b[4] & 255) << 24) //
                + ((b[5] & 255) << 16) //
                + ((b[6] & 255) << 8) //
                + ((b[7] & 255) << 0));
    }

    public static byte[] toBytes(long v) {
        byte[] b = new byte[8];
        b[0] = (byte) (v >>> 56);
        b[1] = (byte) (v >>> 48);
        b[2] = (byte) (v >>> 40);
        b[3] = (byte) (v >>> 32);
        b[4] = (byte) (v >>> 24);
        b[5] = (byte) (v >>> 16);
        b[6] = (byte) (v >>> 8);
        b[7] = (byte) (v >>> 0);
        return b;
    }

    public static byte[] toBytes(int v) throws IOException {
        byte[] b = new byte[4];
        b[0] = (byte) ((v >>> 24) & 0xFF);
        b[1] = (byte) ((v >>> 16) & 0xFF);
        b[2] = (byte) ((v >>> 8) & 0xFF);
        b[3] = (byte) ((v >>> 0) & 0xFF);
        return b;
    }

    public static void close(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
                RxJavaPlugins.onError(e);
            }
        }
    }

    // copied from DataInputStream so don't need to instantiate one
    public static void readFully(InputStream in, byte b[], int off, int len) throws IOException {
        if (len < 0)
            throw new IndexOutOfBoundsException();
        int n = 0;
        while (n < len) {
            int count = in.read(b, off + n, len - n);
            if (count < 0)
                throw new EOFException();
            n += count;
        }
    }
    
    public static long readLong(InputStream in) throws IOException {
        byte[] b = new byte[8];
        readFully(in, b, 0, 8);
        return (((long)b[0] << 56) +
                ((long)(b[1] & 255) << 48) +
                ((long)(b[2] & 255) << 40) +
                ((long)(b[3] & 255) << 32) +
                ((long)(b[4] & 255) << 24) +
                ((b[5] & 255) << 16) +
                ((b[6] & 255) <<  8) +
                ((b[7] & 255) <<  0));
    }
    
    public static byte[] toArray(ByteBuffer bb) {
        int p = bb.position();
        byte[] bytes = new byte[bb.remaining()];
        bb.get(bytes);
        bb.position(p);
        return bytes;
    }
}
