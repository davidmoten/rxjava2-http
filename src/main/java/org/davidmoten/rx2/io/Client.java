package org.davidmoten.rx2.io;

import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.internal.FlowableFromStream;
import org.davidmoten.rx2.io.internal.Util;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.BiConsumer;

public final class Client {

    public static Flowable<ByteBuffer> read(URL url, int preRequest, int bufferSize) {
        BiConsumer<Long, Long> requester = new BiConsumer<Long, Long>() {

            @Override
            public void accept(Long id, Long request) throws Exception {
                // TODO Auto-generated method stub

            }

        };
        return Flowable.using( //
                () -> url.openConnection(), //
                con -> read(Single.fromCallable(() -> con.getInputStream()), requester, preRequest,
                        bufferSize, true), //
                con -> {
                    Util.close(con.getInputStream());
                    Util.close(con.getOutputStream());
                });
    }

    public static Flowable<ByteBuffer> read(Single<InputStream> inSource,
            BiConsumer<Long, Long> requester, int preRequest, int bufferSize, boolean retainSizes) {
        return inSource.flatMapPublisher(
                in -> new FlowableFromStream(in, requester, preRequest, bufferSize, retainSizes));
    }

}
