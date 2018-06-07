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
        private HttpMethod method = HttpMethod.GET;
        private int connectTimeoutMs = 30000;
        private int readTimeoutMs = 0;

        Builder(String url) {
            this.url = url;
        }

        public Builder method(HttpMethod method) {
            this.method = method;
            return this;
        }

        public Builder readTimeoutMs(int timeoutMs) {
            this.readTimeoutMs = timeoutMs;
            return this;
        }

        public Builder connectTimeoutMs(int timeoutMs) {
            this.connectTimeoutMs = timeoutMs;
            return this;
        }

        public <T> Flowable<T> serializer(Serializer<T> serializer) {
            return build().map(serializer::deserialize);
        }

        public <T extends Serializable> Flowable<T> deserialized() {
            return serializer(Serializer.javaIo());
        }

        public Flowable<ByteBuffer> build() {
            return toFlowable(url, method, connectTimeoutMs, readTimeoutMs);
        }
    }

    private static Flowable<ByteBuffer> toFlowable(String url, HttpMethod method,
            int connectTimeoutMs, int readTimeoutMs) {
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
                    con.setConnectTimeout(connectTimeoutMs);
                    con.setReadTimeout(readTimeoutMs);
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
                HttpURLConnection con = (HttpURLConnection) new URL(
                        url + "?id=" + id + "&r=" + request) //
                                .openConnection();
                con.setRequestMethod(method.method());
                con.setUseCaches(false);
                int code = con.getResponseCode();
                if (code != 200) {
                    throw new IOException("response code from request call was not 200: " + code);
                }
            } catch (Throwable e) {
                RxJavaPlugins.onError(e);
            }
        }
    }

    public static Flowable<ByteBuffer> read(Single<InputStream> inSource,
            BiConsumer<Long, Long> requester) {
        return new FlowableSingleFlatMapPublisher<>(inSource,
                in -> new FlowableFromInputStream(in, requester));
    }

}
