package org.davidmoten.rx2.io;

import static org.davidmoten.rx2.io.Servers.createServerAsync;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.davidmoten.rx2.io.Client.Builder;
import org.davidmoten.rx2.io.Client.Options;
import org.davidmoten.rx2.io.Client.Requester;
import org.davidmoten.rx2.io.internal.HttpMethod;
import org.davidmoten.rx2.io.internal.Util;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.davidmoten.junit.Asserts;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.BiConsumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.TestSubscriber;

@org.junit.FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ClientTest {

    private static final Logger log = LoggerFactory.getLogger(ClientTest.class);

    private static final Flowable<ByteBuffer> SOURCE = Flowable.just(
            ByteBuffer.wrap(new byte[] { 1, 2, 3 }), ByteBuffer.wrap(new byte[] { 4, 5, 6, 7 }));

    @Test
    public void isUtilityClass() {
        System.out.println("isUtilityClass");
        Asserts.assertIsUtilityClass(Client.class);
    }

    @Test(expected = RuntimeException.class)
    public void testBadUrl() {
        System.out.println("testBadUrl");
        Client.get("url").build();
    }

    @Test
    public void testGetWithClient() throws Exception {
        System.out.println("testGetWithClient");
        Server server = createServerAsync(SOURCE);
        log.debug("started server");
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(
                    "http://localhost:" + port(server) + "/").openConnection();
            con.setRequestMethod("GET");
            con.setUseCaches(false);
            BiConsumer<Long, Long> requester = createRequester(port(server));
            InputStream in = con.getInputStream();
            log.debug("obtained input stream");
            Client.read(Single.just(in), requester) //
                    .doOnNext(x -> System.out.println(x)) //
                    .reduce(0, (x, bb) -> x + bb.remaining()) //
                    .timeout(2, TimeUnit.SECONDS) //
                    .test() //
                    .awaitDone(5, TimeUnit.SECONDS) //
                    .assertValue(3 + 4) //
                    .assertComplete();
            assertEquals(HttpStatus.OK_200, con.getResponseCode());
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testRequestReturnsNon200ResponseCodeShouldEmitError() throws Exception {
        // TODO
    }

    @Test
    public void testRequestOfUnknownStreamIdShouldEmitError() throws Exception {
        System.out.println("testRequestOfUnknownStreamIdShouldEmitError");
        Server server = createServerAsync(SOURCE);
        try {
            // TODO
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testStreamWithEmptyByteBuffer() throws Exception {
        System.out.println("testStreamWithEmptyByteBuffer");
        Server server = createServerAsync(Flowable.just(ByteBuffer.wrap(new byte[] {})));
        try {
            get(server) //
                    // get coverage of transform builder method
                    .transform(con -> {
                    }) //
                    .build() //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertValueCount(1) //
                    .assertValue(bb -> bb.remaining() == 0) //
                    .assertComplete();
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testStreamWithEmptyByteBufferThenMore() throws Exception {
        System.out.println("testStreamWithEmptyByteBufferThenMore");
        Server server = createServerAsync(Flowable.just(ByteBuffer.wrap(new byte[] {}),
                ByteBuffer.wrap(new byte[] { 1, 2 })));
        try {
            get(server) //
                    .build() //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertValueCount(2) //
                    .assertComplete();
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testGetEmptyStream() throws Exception {
        System.out.println("testGetEmptyStream");
        Server server = createServerAsync(Flowable.empty());
        try {
            get(server) //
                    .build() //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertNoValues() //
                    .assertComplete();
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testGetAsynchronousSource() throws Exception {
        System.out.println("testGetAsynchronousSource");
        Server server = createServerAsync( //
                Flowable.timer(300, TimeUnit.MILLISECONDS) //
                        .map(x -> ByteBuffer.wrap(new byte[] { 1 })));
        try {
            get(server) //
                    .build() //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertValueCount(1) //
                    .assertComplete();
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testError() throws Exception {
        System.out.println("testError");
        RuntimeException ex = new RuntimeException("boo");
        Server server = createServerAsync(Flowable.error(ex));
        try {
            get(server) //
                    .build() //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertNoValues() //
                    .assertError(e -> e.getMessage().startsWith("java.lang.RuntimeException: boo"));
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testValuesThenError() throws Exception {
        System.out.println("testValuesThenError");
        RuntimeException ex = new RuntimeException("boo");
        Server server = createServerAsync( //
                Flowable.just(1, 2, 3) //
                        .concatWith(Flowable.error(ex)) //
                        .map(Serializer.javaIo()::serialize));
        try {
            get(server) //
                    .deserialized() //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertValues(1, 2, 3) //
                    .assertError(e -> e.getMessage().startsWith("java.lang.RuntimeException: boo"));
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testCancel() throws Exception {
        System.out.println("testCancel");
        Server server = createServerAsync(
                Flowable.just(ByteBuffer.wrap(new byte[] { 1 })).repeat());
        try {
            get(server) //
                    .build() //
                    .take(20) //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) // s
                    .assertValueCount(20) //
                    .assertComplete();
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testLongStream() throws Exception {
        System.out.println("testLongStream");
        Flowable<ByteBuffer> flowable = Flowable.rangeLong(1, Long.MAX_VALUE)
                .map(n -> ByteBuffer.wrap(Util.toBytes(n)));
        Server server = createServerAsync(flowable);
        long n = 1000000;
        long t = System.currentTimeMillis();
        long[] count = new long[1];
        try {
            get(server) //
                    .connectTimeoutMs(5000) //
                    .readTimeoutMs(30000) //
                    .build() //
                    .doOnNext(bb -> {
                        if (count[0]++ % 100000 == 0)
                            System.out.println(
                                    (System.currentTimeMillis() - t) / 1000 + "s:" + count[0]);
                    }) //
                    .rebatchRequests(20000).skip(n) //
                    .take(1) //
                    .map(bb -> bb.getLong()) //
                    .test() //
                    .awaitDone(300, TimeUnit.SECONDS) //
                    .assertValue(n + 1) //
                    .assertComplete();
            System.out.println((1000 * n / (System.currentTimeMillis() - t)) + " items/s");
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void test40ByteStream() throws Exception {
        System.out.println("test40ByteStream");
        testByteStream(40, 100000);
    }

    @Test
    public void test400ByteStream() throws Exception {
        System.out.println("test400ByteStream");
        testByteStream(400, 10000);
    }

    @Test
    public void testLongByteArrayStream() throws Exception {
        System.out.println("testLongByteArrayStream");
        testByteStream(131072, 10000);
    }

    @Test
    public void test4000ByteStream() throws Exception {
        System.out.println("test4000ByteStream");
        testByteStream(4000, 1000);
    }

    private void testByteStream(int numBytes, int numItems) throws Exception {
        ByteBuffer b = ByteBuffer.wrap(new byte[numBytes]);
        Flowable<ByteBuffer> flowable = Flowable //
                .just(b).repeat();
        Server server = createServerAsync(flowable);
        long n = numItems;
        long t = System.currentTimeMillis();
        long[] count = new long[1];
        try {
            get(server) //
                    .connectTimeoutMs(5000) //
                    .readTimeoutMs(30000) //
                    .build() //
                    .doOnNext(bb -> {
                        if (count[0]++ % 100000 == 0)
                            System.out.println(
                                    (System.currentTimeMillis() - t) / 1000 + "s:" + count[0]);
                    }) //
                    .skip(n) //
                    .take(1) //
                    .test() //
                    .awaitDone(300, TimeUnit.SECONDS) //
                    .assertValueCount(1) //
                    .assertComplete();
            long itemsPerSecond = 1000 * n / (System.currentTimeMillis() - t);
            DecimalFormat df = new DecimalFormat("0.000");
            System.out.println("numBytes=" + numBytes + ", " + itemsPerSecond + " items/s, "
                    + df.format((itemsPerSecond * numBytes) / 1024.0 / 1024.0) + "MB/s");
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testRangeManyTimes() throws Exception {
        System.out.println("testRangeManyTimes");
        Flowable<ByteBuffer> flowable = Flowable.range(1, 1000).map(Serializer.javaIo()::serialize);
        Server server = createServerAsync(flowable);
        try {
            for (int i = 0; i < 1000; i++) {
                get(server) //
                        .<Integer>deserialized() //
                        .skip(500) //
                        .take(4) //
                        .test() //
                        .awaitDone(10, TimeUnit.SECONDS) //
                        .assertValues(501, 502, 503, 504) //
                        .assertComplete();

            }
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testRangeRebatchedManyTimes() throws Exception {
        System.out.println("testRangeRebatchedManyTimes");
        Flowable<ByteBuffer> flowable = Flowable.range(1, 1000).map(Serializer.javaIo()::serialize);
        Server server = createServerAsync(flowable);
        try {
            for (int i = 0; i < 10; i++) {
                get(server) //
                        .<Integer>deserialized() //
                        .rebatchRequests(2) //
                        .skip(500) //
                        .take(4) //
                        .test() //
                        .awaitDone(10, TimeUnit.SECONDS) //
                        .assertValues(501, 502, 503, 504) //
                        .assertComplete();

            }
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testRangeAsync() throws Exception {
        System.out.println("testRangeAsync");
        Flowable<ByteBuffer> flowable = Flowable.range(1, 1000).map(Serializer.javaIo()::serialize)
                .observeOn(Schedulers.io());
        Server server = createServerAsync(flowable);
        try {
            get(server) //
                    .<Integer>deserialized() //
                    .rebatchRequests(10) //
                    .doOnRequest(System.out::println) //
                    .skip(500) //
                    .take(4) //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertValues(501, 502, 503, 504) //
                    .assertComplete();
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testBackpressure() throws Exception {
        System.out.println("testBackpressure");
        List<Long> requests = new CopyOnWriteArrayList<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        Flowable<ByteBuffer> flowable = Flowable.range(1, 1000) //
                .map(Serializer.javaIo()::serialize) //
                .doOnRequest(n -> requests.add(n)) //
                .doOnCancel(() -> cancelled.set(true));
        Server server = createServerAsync(flowable);
        try {
            TestSubscriber<Integer> ts = get(server) //
                    .<Integer>deserialized() //
                    .test(0);
            Thread.sleep(300);
            ts.assertNoValues() //
                    .assertNotTerminated();
            // 16 pre-request and 16 from flatmap
            assertEquals(Arrays.asList(), requests);
            ts.requestMore(1);
            Thread.sleep(300);
            ts.assertValue(1);
            assertEquals(Arrays.asList(1L), requests);
            ts.requestMore(2);
            Thread.sleep(300);
            ts.assertValues(1, 2, 3);
            ts.requestMore(2);
            Thread.sleep(300);
            ts.assertValues(1, 2, 3, 4, 5);
            ts.cancel();
            Thread.sleep(300);
            assertTrue(cancelled.get());
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testRangeParallel() throws Exception {
        System.out.println("testRangeParallel");
        Flowable<ByteBuffer> flowable = Flowable.range(1, 1000).map(Serializer.javaIo()::serialize);
        Server server = createServerAsync(flowable);
        try {
            Flowable<Integer> f = get(server) //
                    .<Integer>deserialized() //
                    .skip(500) //
                    .take(4);
            f.zipWith(f, (a, b) -> a) //
                    .timeout(5, TimeUnit.SECONDS) //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertValues(501, 502, 503, 504) //
                    .assertComplete();
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testRangeParallelSync() throws Exception {
        System.out.println("testRangeParallelSync");
        Flowable<ByteBuffer> flowable = Flowable.range(1, 1000).map(Serializer.javaIo()::serialize);
        Server server = createServerSync(flowable);
        try {
            Flowable<Integer> f = get(server) //
                    .<Integer>deserialized() //
                    .skip(500) //
                    .take(4);
            f.zipWith(f, (a, b) -> a) //
                    // .timeout(5, TimeUnit.SECONDS, Schedulers.io()) //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertResult(501, 502, 503, 504);
        } finally {
            // Stop Server
            server.stop();
        }
    }

    private static Builder get(Server server) {
        return Client.get("http://localhost:" + port(server) + "/");
    }

    private static Builder post(Server server) {
        return Client.post("http://localhost:" + port(server) + "/");
    }

    private static int port(Server server) {
        return ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    }

    @Test
    public void testGetWithClient2() throws Exception {
        System.out.println("testGetWithClient2");
        Server server = createServerAsync(SOURCE);
        try {
            get(server) //
                    .build() //
                    .reduce(0, (x, bb) -> x + bb.remaining()) //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertValue(3 + 4) //
                    .assertComplete();
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testGetWithClientHttpPost() throws Exception {
        System.out.println("testGetWithClientHttpPost");
        Server server = createServerAsync(SOURCE);
        try {
            post(server) //
                    .build() //
                    .reduce(0, (x, bb) -> x + bb.remaining()) //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertValue(3 + 4) //
                    .assertComplete();
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testFlowableFactoryThrows() throws Exception {
        System.out.println("testFlowableFactoryThrows");
        Server server = createServerFlowableFactoryThrows();
        try {
            get(server) //
                    .build() //
                    .reduce(0, (x, bb) -> x + bb.remaining()) //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertNoValues() //
                    .assertError(e -> e.getMessage().startsWith("java.lang.RuntimeException: boo"));
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testSimpleGet() throws Exception {
        System.out.println("testSimpleGet");
        Server server = createServerAsync(SOURCE);
        try {
            // Start Server
            server.start();
            HttpURLConnection con = (HttpURLConnection) new URL(
                    "http://localhost:" + port(server) + "/?r=100").openConnection();
            con.setRequestMethod("GET");
            con.setUseCaches(false);
            InputStream in = con.getInputStream();
            byte[] bytes = new byte[8192];
            int count = 0;
            int n;
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            while ((n = in.read(bytes)) != -1) {
                count += n;
                b.write(bytes, 0, n);
            }
            in.close();
            System.out.println(Arrays.toString(b.toByteArray()));
            assertEquals(27, count);
        } finally {
            server.stop();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClientNegativeTimeoutThrows() {
        System.out.println("testClientNegativeTimeoutThrows");
        Client.get("http://blah").connectTimeoutMs(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClientNegativeReadTimeoutThrows() {
        System.out.println("testClientNegativeReadTimeoutThrows");
        Client.get("http://blah").readTimeoutMs(-1);
    }

    @Test(expected = NullPointerException.class)
    public void testClientProxyNullThrows() {
        System.out.println("testClientProxyNullThrows");
        Client.get("http://blah").proxy(null);
    }

    @Test(expected = NullPointerException.class)
    public void testClientProxyNullHostThrows() {
        System.out.println("testClientProxyNullHostThrows");
        Client.get("http://blah").proxy(null, 8080);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClientProxyNegativePortThrows() {
        System.out.println("testClientProxyNegativePortThrows");
        Client.get("http://blah").proxy("proxy", -1);
    }

    @Test
    public void testClientValidProxyDoesNotThrow() {
        System.out.println("testClientValidProxyDoesNotThrow");
        Client.get("http://blah").proxy("proxy", 8080);
    }
    
    @Test(expected = NullPointerException.class)
    public void testRequestHeaderNullThrows() {
        System.out.println("testRequestHeaderNullThrows");
        Client.get("http://blah").requestHeader(null, "a");
    }
    
    @Test(expected = NullPointerException.class)
    public void testRequestHeaderNullValueThrows() {
        System.out.println("testRequestHeaderNullValueThrows");
        Client.get("http://blah").requestHeader("a", null);
    }


    @Test
    public void testRequesterNon200ResponseCode() throws Exception {
        System.out.println("testRequesterNon200ResponseCode");
        Requester r = new Client.Requester("http://localhost/doesNotExist",
                new Options(HttpMethod.GET, 1000, 1000, Collections.emptyMap(), null,
                        Collections.emptyList(), null, Schedulers.trampoline()));
        r.accept(1L, 1L);
    }

    @Test
    public void testRangeParallelLongRunning() throws Exception {
        System.out.println("testRangeParallelLongRunning");
        Flowable<ByteBuffer> flowable = Flowable //
                .rangeLong(1, Long.MAX_VALUE) //
                .map(x -> ByteBuffer.wrap(Util.toBytes(x)));
        Server server = createServerAsync(flowable);
        final long N = Long.parseLong(System.getProperty("N", "100000"));
        try {
            Flowable<Long> f = Flowable.defer(() -> {
                AtomicLong count = new AtomicLong();
                return get(server) //
                        .<Long>deserializer(bb -> bb.getLong()) //
                        .doOnNext(n -> {
                            long c = count.incrementAndGet();
                            if (c % 100000 == 0) {
                                log.info("count={}", c);
                            }
                            assertEquals((long) n, c);
                        }) //
                        .take(N) //
                        .observeOn(Schedulers.io());
            });
            f.zipWith(f, (a, b) -> a) //
                    .count() //
                    .test() //
                    .awaitDone(Long.MAX_VALUE, TimeUnit.DAYS) //
                    .assertValue(N) //
                    .assertComplete();
        } finally {
            // Stop Server
            server.stop();
        }
    }

    private static Server createServerFlowableFactoryThrows() {
        // Create Server
        Server server = new Server(0);
        ServletContextHandler context = new ServletContextHandler();
        ServletHolder defaultServ = new ServletHolder("default", HandlerServletFactoryThrows.class);
        defaultServ.setInitParameter("resourceBase", System.getProperty("user.dir"));
        defaultServ.setInitParameter("dirAllowed", "true");
        context.addServlet(defaultServ, "/");
        server.setHandler(context);
        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return server;
    }

    private static Server createServerSync(Flowable<ByteBuffer> flowable) {
        // Create Server
        Server server = new Server(0);
        ServletContextHandler context = new ServletContextHandler();
        ServletHolder defaultServ = new ServletHolder("default", ServletSync.class);
        defaultServ.setInitParameter("resourceBase", System.getProperty("user.dir"));
        defaultServ.setInitParameter("dirAllowed", "true");
        context.addServlet(defaultServ, "/");
        server.setHandler(context);
        ServletSync.flowable = flowable;
        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return server;
    }

    private static BiConsumer<Long, Long> createRequester(int port) {
        return new BiConsumer<Long, Long>() {

            @Override
            public void accept(Long id, Long request) throws Exception {
                log.debug("requesting id={}, n={}", id, request);
                HttpURLConnection con = (HttpURLConnection) new URL(
                        "http://localhost:" + port + "/?id=" + id + "&r=" + request)
                                .openConnection();
                con.setRequestMethod("GET");
                con.setUseCaches(false);
                assertEquals(HttpStatus.OK_200, con.getResponseCode());
            }
        };
    }

}
