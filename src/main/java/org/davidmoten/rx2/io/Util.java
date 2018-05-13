package org.davidmoten.rx2.io;

import java.io.IOException;

public class Util {
    
    private Util() {
        //prevent instantiation
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

    public byte[] toBytes(int v) throws IOException {
        byte[] b = new byte[4];
        b[0] = (byte) ((v >>> 24) & 0xFF);
        b[1] = (byte) ((v >>> 16) & 0xFF);
        b[2] = (byte) ((v >>> 8) & 0xFF);
        b[3] = (byte) ((v >>> 0) & 0xFF);
        return b;
    }

}
