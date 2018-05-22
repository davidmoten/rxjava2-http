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

import com.github.davidmoten.guavamini.Preconditions;

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
        private int bufferSize = 16;
        private boolean delayErrors = false;

        Builder(String url) {
            this.url = url;
        }

        public Builder bufferSize(int n) {
            this.bufferSize = n;
            return this;
        }

        public Builder delayErrors(boolean delayErrors) {
            this.delayErrors = delayErrors;
            return this;
        }

        public <T> Flowable<T> serializer(Serializer<T> serializer) {
            return get(url, delayErrors, bufferSize, serializer);
        }

        public <T extends Serializable> Flowable<T> deserialized() {
            return serializer(DefaultSerializer.instance());
        }

        public Flowable<ByteBuffer> build() {
            return get(url, delayErrors, bufferSize);
        }
    }

    private static <T> Flowable<T> get(String url, boolean delayErrors, int bufferSize, Serializer<T> serializer) {
        return get(url, delayErrors, bufferSize).map(serializer::deserialize);
    }

    private static Flowable<ByteBuffer> get(String url, boolean delayErrors, int bufferSize) {
        Preconditions.checkArgument(bufferSize > 0);
        final URL u;
        try {
            u = new URL(url + "/?r=" + bufferSize);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        BiConsumer<Long, Long> requester = new Requester(url);

        return Flowable.using( //
                () -> {
                    HttpURLConnection con = (HttpURLConnection) u.openConnection();
                    con.setRequestMethod("GET");
                    con.setUseCaches(false);
                    return con.getInputStream();
                }, //
                in -> read(Single.just(in), requester, delayErrors, bufferSize), //
                in -> Util.close(in));
    }

    static final class Requester implements BiConsumer<Long, Long> {

        private final String url;

        Requester(String url) {
            this.url = url;
        }

        @Override
        public void accept(Long id, Long request) throws Exception {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url + "?id=" + id + "&r=" + request) //
                        .openConnection();
                con.setRequestMethod("GET");
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

    public static Flowable<ByteBuffer> read(Single<InputStream> inSource, BiConsumer<Long, Long> requester,
            boolean delayErrors, int bufferSize) {
        return new FlowableSingleFlatMapPublisher<>(inSource, in -> new FlowableFromInputStream(in, requester))
                .rebatchRequests(bufferSize);
    }

}
