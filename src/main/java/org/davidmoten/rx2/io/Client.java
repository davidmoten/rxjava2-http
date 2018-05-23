package org.davidmoten.rx2.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.internal.FlowableFromInputStream;
import org.davidmoten.rx2.io.internal.FlowableSingleFlatMapPublisher;
import org.davidmoten.rx2.io.internal.Util;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.BiConsumer;
import io.reactivex.plugins.RxJavaPlugins;

public final class Client {

    private Client() {
        // prevent instantiation
    }

    public static Builder get(String url) {
        return new Builder(url);
    }

    static final class Builder {

        private final String url;
        private int bufferSize = 0;
        private HttpMethod method = HttpMethod.GET;

        Builder(String url) {
            this.url = url;
        }

        public Builder bufferSize(int n) {
            this.bufferSize = n;
            return this;
        }

        public Builder method(HttpMethod method) {
            this.method = method;
            return this;
        }

        public <T> Flowable<T> serializer(Serializer<T> serializer) {
            return build().map(serializer::deserialize);
        }

        public <T extends Serializable> Flowable<T> deserialized() {
            return serializer(Serializer.javaIo());
        }

        public Flowable<ByteBuffer> build() {
            if (bufferSize == 0) {
                return toFlowable(url, method);
            } else {
                return toFlowable(url, method).rebatchRequests(bufferSize);
            }
        }
    }

    private static Flowable<ByteBuffer> toFlowable(String url, HttpMethod method) {
        URL u;
        try {
            u = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        BiConsumer<Long, Long> requester = new Requester(url, method);

        return Flowable.using( //
                () -> {
                    HttpURLConnection con = (HttpURLConnection) u.openConnection();
                    con.setRequestMethod(method.method());
                    con.setUseCaches(false);
                    return con.getInputStream();
                }, //
                in -> read(Single.just(in), requester), //
                in -> Util.close(in));
    }

    static final class Requester implements BiConsumer<Long, Long> {

        private final String url;
        private final HttpMethod method;

        Requester(String url, HttpMethod method) {
            this.url = url;
            this.method = method;
        }

        @Override
        public void accept(Long id, Long request) throws Exception {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url + "?id=" + id + "&r=" + request) //
                        .openConnection();
                con.setRequestMethod(method.method());
                con.setUseCaches(false);
                int code = con.getResponseCode();
                if (code != 200) {
                    RxJavaPlugins.onError(new IOException("response code from request call was not 200: " + code));
                }
            } catch (Throwable e) {
                RxJavaPlugins.onError(e);
            }
        }
    }

    public static Flowable<ByteBuffer> read(Single<InputStream> inSource, BiConsumer<Long, Long> requester) {
        return new FlowableSingleFlatMapPublisher<>(inSource, in -> new FlowableFromInputStream(in, requester)).doOnRequest(n ->System.out.println("requested==" + n));
    }

}
