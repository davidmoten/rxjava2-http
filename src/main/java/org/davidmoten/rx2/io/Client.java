package org.davidmoten.rx2.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.davidmoten.rx2.io.internal.FlowableFromInputStream;
import org.davidmoten.rx2.io.internal.FlowableSingleFlatMapPublisher;
import org.davidmoten.rx2.io.internal.HttpMethod;
import org.davidmoten.rx2.io.internal.Util;

import com.github.davidmoten.guavamini.Preconditions;
import com.github.davidmoten.guavamini.annotations.VisibleForTesting;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;

public final class Client {

    private Client() {
        // prevent instantiation
    }

    public static Builder get(String url) {
        return new Builder(url, HttpMethod.GET);
    }
    
    public static Builder post(String url) {
        return new Builder(url, HttpMethod.POST);
    }

    static final class Builder {

        private final String url;
        private final HttpMethod method ;
        private int connectTimeoutMs = 30000;
        private int readTimeoutMs = 0;
        private Map<String, String> requestHeaders = new HashMap<>();
        private SSLSocketFactory sslSocketFactory;
        private List<Consumer<HttpURLConnection>> transforms = new ArrayList<>();
        private Proxy proxy;

        Builder(String url, HttpMethod method) {
            this.url = url;
            this.method = method;
        }

        public Builder readTimeoutMs(int timeoutMs) {
            this.readTimeoutMs = timeoutMs;
            return this;
        }

        public Builder connectTimeoutMs(int timeoutMs) {
            this.connectTimeoutMs = timeoutMs;
            return this;
        }

        public Builder proxy(String host, int port) {
            Preconditions.checkNotNull(host);
            Preconditions.checkArgument(port > 0);
            return proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
        }

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder transform(Consumer<HttpURLConnection> transform) {
            this.transforms.add(transform);
            return this;
        }

        public Builder basicAuth(String username, String password) {
            Preconditions.checkNotNull(username);
            Preconditions.checkNotNull(password);
            String s = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            return requestHeader("Authorization", "Basic " + s);
        }

        public Builder requestHeader(String key, String value) {
            requestHeaders.put(key, value);
            return this;
        }

        public Builder sslSocketFactory(SSLSocketFactory sslSocketFactory) {
            this.sslSocketFactory = sslSocketFactory;
            return this;
        }

        public Builder sslContext(SSLContext sslContext) {
            return sslSocketFactory(sslContext.getSocketFactory());
        }

        public <T> Flowable<T> serializer(Serializer<T> serializer) {
            return build().map(serializer::deserialize);
        }

        public <T extends Serializable> Flowable<T> deserialized() {
            return serializer(Serializer.javaIo());
        }

        public Flowable<ByteBuffer> build() {
            return toFlowable(url, new Options(method, connectTimeoutMs, readTimeoutMs, requestHeaders,
                    sslSocketFactory, transforms, proxy));
        }
    }

    private static Flowable<ByteBuffer> toFlowable(String url, Options options) {
        URL u;
        try {
            u = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        BiConsumer<Long, Long> requester = new Requester(url, options);

        return Flowable.using( //
                () -> {
                    final HttpURLConnection con = open(u, options);
                    prepareConnection(con, options);
                    return con.getInputStream();
                }, //
                in -> read(Single.just(in), requester), //
                in -> Util.close(in));
    }

    private static HttpURLConnection open(URL url, Options options) throws IOException {
        if (options.proxy == null) {
            return (HttpURLConnection) url.openConnection();
        } else {
            return (HttpURLConnection) url.openConnection(options.proxy);
        }
    }

    private static void prepareConnection(HttpURLConnection con, Options options) throws ProtocolException {
        con.setRequestMethod(options.method.method());
        con.setUseCaches(false);
        con.setConnectTimeout(options.connectTimeoutMs);
        con.setReadTimeout(options.readTimeoutMs);
        options.requestHeaders.entrySet().stream()
                .forEach(entry -> con.setRequestProperty(entry.getKey(), entry.getValue()));
        if (options.sslSocketFactory != null && con instanceof HttpsURLConnection) {
            ((HttpsURLConnection) con).setSSLSocketFactory(options.sslSocketFactory);
        }
        transform(con, options.transforms);
    }

    private static void transform(final HttpURLConnection con, List<Consumer<HttpURLConnection>> transforms) {
        transforms.stream().forEach(transform -> {
            try {
                transform.accept(con);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @VisibleForTesting
    static final class Options {
        final HttpMethod method;
        final int connectTimeoutMs;
        int readTimeoutMs;
        final Map<String, String> requestHeaders;
        final SSLSocketFactory sslSocketFactory;
        final List<Consumer<HttpURLConnection>> transforms;
        final Proxy proxy;

        Options(HttpMethod method, int connectTimeoutMs, int readTimeoutMs, Map<String, String> requestHeaders,
                SSLSocketFactory sslSocketFactory, List<Consumer<HttpURLConnection>> transforms, Proxy proxy) {
            this.method = method;
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
            this.requestHeaders = requestHeaders;
            this.sslSocketFactory = sslSocketFactory;
            this.transforms = transforms;
            this.proxy = proxy;
        }
    }

    static final class Requester implements BiConsumer<Long, Long> {

        private final String url;
        private final Options options;

        Requester(String url, Options options) {
            this.url = url;
            this.options = options;
        }

        @Override
        public void accept(Long id, Long request) throws Exception {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(url + "?id=" + id + "&r=" + request) //
                        .openConnection();
                prepareConnection(con, options);
                int code = con.getResponseCode();
                if (code != 200) {
                    throw new IOException("response code from request call was not 200: " + code);
                }
            } catch (Throwable e) {
                RxJavaPlugins.onError(e);
            }
        }
    }

    public static Flowable<ByteBuffer> read(Single<InputStream> inSource, BiConsumer<Long, Long> requester) {
        return new FlowableSingleFlatMapPublisher<>(inSource, in -> new FlowableFromInputStream(in, requester));
    }

}
