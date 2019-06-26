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
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.functions.BiConsumer;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;

public final class Client {

    private Client() {
        // prevent instantiation
    }

    /**
     * Returns a Builder for an over-network {@link Flowable}. The HTTP verb used
     * with the {@code url} will be {@code GET}.
     * 
     * @param url
     *            location of the stream to connect to using HTTP GET
     * @return Builder for an over-network {@code Flowable}
     */
    public static Builder get(String url) {
        Preconditions.checkNotNull(url);
        return new Builder(url, HttpMethod.GET);
    }

    /**
     * Returns a Builder for an over-network {@link Flowable}. The HTTP verb used
     * with the {@code url} will be {@code POST}.
     * 
     * @param url
     *            location of the stream to connect to using HTTP POST
     * @return Builder for an over-network {@code Flowable}
     */
    public static Builder post(String url) {
        Preconditions.checkNotNull(url);
        return new Builder(url, HttpMethod.POST);
    }

    public static final class Builder {

        private final String url;
        private final HttpMethod method;
        private int connectTimeoutMs = 30000;
        private int readTimeoutMs = 0;
        private Map<String, String> requestHeaders = new HashMap<>();
        private SSLSocketFactory sslSocketFactory;
        private List<Consumer<HttpURLConnection>> transforms = new ArrayList<>();
        private Proxy proxy;
        private Scheduler requestScheduler = Schedulers.trampoline();

        Builder(String url, HttpMethod method) {
            this.url = url;
            this.method = method;
        }

        /**
         * Sets the read timeout in ms for the HTTP connection. Default is zero which is
         * advisable for a long-running occasionally quiet stream. When a read timeout
         * occurs the {@link Flowable} will emit an error.
         * 
         * @param timeoutMs
         *            read timeout for the HTTP connection.
         * @return this
         */
        public Builder readTimeoutMs(int timeoutMs) {
            Preconditions.checkArgument(timeoutMs >= 0);
            this.readTimeoutMs = timeoutMs;
            return this;
        }

        /**
         * Sets the connect timeout in ms for the HTTP connection. Default is 30s which
         * is When a read timeout occurs the {@link Flowable} will emit an error.
         * 
         * @param timeoutMs
         *            connect timeout for the HTTP connection.
         * @return this
         */
        public Builder connectTimeoutMs(int timeoutMs) {
            Preconditions.checkArgument(timeoutMs >= 0);
            this.connectTimeoutMs = timeoutMs;
            return this;
        }

        /**
         * Sets the proxy details for the HTTP connection.
         * 
         * @param host
         *            proxy host
         * @param port
         *            proxy port
         * @return this
         */
        public Builder proxy(String host, int port) {
            Preconditions.checkNotNull(host);
            Preconditions.checkArgument(port > 0);
            return proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port)));
        }

        /**
         * Sets the proxy for the HTTP connection.
         * 
         * @param proxy
         *            the proxy details
         * @return this
         */
        public Builder proxy(Proxy proxy) {
            Preconditions.checkNotNull(proxy);
            this.proxy = proxy;
            return this;
        }

        /**
         * Sets the actions that should be applied to {@link HttpURLConnection} before
         * calling {@code HttpURLConnection.getInputStream()}. This applies to all HTTP
         * calls being the subscription, request and cancel calls.
         * 
         * @param transform
         *            action to apply
         * @return this
         */
        public Builder transform(Consumer<HttpURLConnection> transform) {
            Preconditions.checkNotNull(transform);
            this.transforms.add(transform);
            return this;
        }

        /**
         * Sets the Basic Authentication details for all HTTP connections (subscription,
         * request and cancellation).
         * 
         * @param username
         *            authentication username
         * @param password
         *            authentication password
         * @return this
         */
        public Builder basicAuth(String username, String password) {
            Preconditions.checkNotNull(username);
            Preconditions.checkNotNull(password);
            String s = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
            return requestHeader("Authorization", "Basic " + s);
        }

        /**
         * Sets a request header for the HTTP connection.
         * 
         * @param key
         *            request header key
         * @param value
         *            request header value
         * @return this
         */
        public Builder requestHeader(String key, String value) {
            Preconditions.checkNotNull(key);
            Preconditions.checkNotNull(value);
            requestHeaders.put(key, value);
            return this;
        }

        /**
         * Sets the {@link SSLSocketFactory} to be used for HTTPS connections.
         * 
         * @param sslSocketFactory
         *            ssl socket factory
         * @return this
         */
        public Builder sslSocketFactory(SSLSocketFactory sslSocketFactory) {
            Preconditions.checkNotNull(sslSocketFactory);
            this.sslSocketFactory = sslSocketFactory;
            return this;
        }

        /**
         * Sets the {@link SSLContext} to be used for HTTP connections. The socket
         * factory will be obtained from the SSLContext.
         * 
         * @param sslContext
         *            ssl context
         * @return this
         */
        public Builder sslContext(SSLContext sslContext) {
            Preconditions.checkNotNull(sslContext);
            return sslSocketFactory(sslContext.getSocketFactory());
        }

        public Builder requestScheduler(Scheduler scheduler) {
            this.requestScheduler = scheduler;
            return this;
        }

        /**
         * Sets the deserializer to be used on the arriving {@link ByteBuffer}s.
         * 
         * @param <T> stream type
         * @param serializer the deserializer to be used
         * @return this
         */
        public <T> Flowable<T> deserializer(Deserializer<T> serializer) {
            Preconditions.checkNotNull(serializer);
            return build().map(serializer::deserialize);
        }

        /**
         * Returns the {@link Flowable} where deserialization is performed by
         * {@link Serializer#javaIo()}.
         * 
         * @param <T>
         *            the Flowable result type
         * @return the built Flowable
         */
        public <T extends Serializable> Flowable<T> deserialized() {
            return deserializer(Serializer.javaIo());
        }

        /**
         * Returns the built {@code Flowable<ByteBuffer>} based on all the builder
         * options specified.
         * 
         * @return the built Flowable.
         */
        public Flowable<ByteBuffer> build() {
            return toFlowable(url, new Options(method, connectTimeoutMs, readTimeoutMs,
                    requestHeaders, sslSocketFactory, transforms, proxy, requestScheduler));
        }
    }

    public static Flowable<ByteBuffer> read(Single<InputStream> inSource,
            BiConsumer<Long, Long> requester) {
        return new FlowableSingleFlatMapPublisher<>(inSource,
                in -> new FlowableFromInputStream(in, requester));
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

    private static void prepareConnection(HttpURLConnection con, Options options)
            throws ProtocolException {
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

    private static void transform(final HttpURLConnection con,
            List<Consumer<HttpURLConnection>> transforms) {
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
        final Scheduler requestScheduler;

        Options(HttpMethod method, int connectTimeoutMs, int readTimeoutMs,
                Map<String, String> requestHeaders, SSLSocketFactory sslSocketFactory,
                List<Consumer<HttpURLConnection>> transforms, Proxy proxy,
                Scheduler requestScheduler) {
            this.method = method;
            this.connectTimeoutMs = connectTimeoutMs;
            this.readTimeoutMs = readTimeoutMs;
            this.requestHeaders = requestHeaders;
            this.sslSocketFactory = sslSocketFactory;
            this.transforms = transforms;
            this.proxy = proxy;
            this.requestScheduler = requestScheduler;
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
            options.requestScheduler.scheduleDirect(() -> {
                try {
                    HttpURLConnection con = (HttpURLConnection) new URL(
                            url + "?id=" + id + "&r=" + request) //
                                    .openConnection();
                    prepareConnection(con, options);
                    int code = con.getResponseCode();
                    if (code != 200) {
                        throw new IOException(
                                "response code from request call was not 200: " + code);
                    }
                } catch (Throwable e) {
                    RxJavaPlugins.onError(e);
                }
            });
        }
    }

}
