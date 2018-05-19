package org.davidmoten.rx2.io;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.internal.FlowableFromStream;
import org.davidmoten.rx2.io.internal.Util;
import org.eclipse.jetty.http.HttpStatus;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.BiConsumer;

public final class Client {

    public static Flowable<ByteBuffer> read(String url, int preRequest) {
        final URL u;
        try {
            if (preRequest == 0) {
                u = new URL(url);
            } else {
                u = new URL(url + "/?r=" + preRequest);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        BiConsumer<Long, Long> requester = new BiConsumer<Long, Long>() {

            @Override
            public void accept(Long id, Long request) throws Exception {
                HttpURLConnection con = (HttpURLConnection) new URL(
                        url + "?id=" + id + "&r=" + request) //
                                .openConnection();
                con.setRequestMethod("GET");
                con.setUseCaches(false);
                assertEquals(HttpStatus.OK_200, con.getResponseCode());
            }

        };

        return Flowable.using( //
                () -> {
                    HttpURLConnection con = (HttpURLConnection) u.openConnection();
                    con.setRequestMethod("GET");
                    con.setUseCaches(false);
                    return con;
                }, //
                con -> read(Single.fromCallable(() -> con.getInputStream()), requester), //
                con -> Util.close(con.getInputStream()));
    }

    public static Flowable<ByteBuffer> read(Single<InputStream> inSource,
            BiConsumer<Long, Long> requester) {
        return inSource.flatMapPublisher(in -> new FlowableFromStream(in, requester));
    }

}
