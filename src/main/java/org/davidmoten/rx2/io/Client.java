package org.davidmoten.rx2.io;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;

import io.reactivex.Flowable;
import io.reactivex.functions.Consumer;

public final class Client {

    public static Flowable<ByteBuffer> read(URL url, int preRequest, int bufferSize) {
        Consumer<Long> requester = new Consumer<Long>() {

            @Override
            public void accept(Long t) throws Exception {
                // TODO Auto-generated method stub

            }

        };
        return Flowable.using( //
                () -> url.openConnection(), //
                con -> read(con, requester, preRequest, bufferSize), //
                con -> {
                    Util.close(con.getInputStream());
                    Util.close(con.getOutputStream());
                });
    }

    public static Flowable<ByteBuffer> read(URLConnection con, Consumer<Long> requester, int preRequest,
            int bufferSize) {
        try {
            con.connect();
            InputStream in = con.getInputStream();
            return new FlowableHttp(in, requester, preRequest, bufferSize);
        } catch (IOException e) {
            return Flowable.error(e);
        }
    }

}
