package org.davidmoten.rx2.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;

import io.reactivex.Flowable;

public final class Client {

    public static Flowable<ByteBuffer> read(URL url, int preRequest, int bufferSize) {
        return Flowable.using( //
                () -> url.openConnection(), //
                con -> read(con, preRequest, bufferSize), //
                con -> {
                    Util.close(con.getInputStream());
                    Util.close(con.getOutputStream());
                });
    }

    public static Flowable<ByteBuffer> read(URLConnection con, int preRequest, int bufferSize) {
        try {
            con.connect();
            InputStream in = con.getInputStream();
            OutputStream out = con.getOutputStream();
            return new FlowableHttp(in, out, preRequest, bufferSize);
        } catch (IOException e) {
            return Flowable.error(e);
        }
    }

}
