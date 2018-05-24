package org.davidmoten.rx2.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.davidmoten.rx2.io.Client.Requester;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Test;

import com.github.davidmoten.junit.Asserts;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.BiConsumer;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.TestSubscriber;

public class ClientTest {

    private static final Flowable<ByteBuffer> SOURCE = Flowable.just(ByteBuffer.wrap(new byte[] { 1, 2, 3 }),
            ByteBuffer.wrap(new byte[] { 4, 5, 6, 7 }));

    @Test
    public void isUtilityClass() {
        Asserts.assertIsUtilityClass(Client.class);
    }

    @Test(expected = RuntimeException.class)
    public void testBadUrl() {
        Client.get("url").build();
    }

    @Test
    public void testGetWithClient() throws Exception {
        Server server = createServerAsync(SOURCE);
        try {
            HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8080/").openConnection();
            con.setRequestMethod("GET");
            con.setUseCaches(false);
            BiConsumer<Long, Long> requester = createRequester();
            Client.read(Single.fromCallable(() -> con.getInputStream()), requester) //
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
     //TODO   
    }
    
    @Test
    public void testRequestOfUnknownStreamIdShouldEmitError() throws Exception {
        Server server = createServerAsync(SOURCE);
        try {
            //TODO
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testStreamWithEmptyByteBuffer() throws Exception {
        Server server = createServerAsync(Flowable.just(ByteBuffer.wrap(new byte[] {})));
        try {
            Client.get("http://localhost:8080/") //
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
        Server server = createServerAsync(
                Flowable.just(ByteBuffer.wrap(new byte[] {}), ByteBuffer.wrap(new byte[] { 1, 2 })));
        try {
            Client.get("http://localhost:8080/") //
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
        Server server = createServerAsync(Flowable.empty());
        try {
            Client.get("http://localhost:8080/") //
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
        Server server = createServerAsync( //
                Flowable.timer(300, TimeUnit.MILLISECONDS) //
                        .map(x -> ByteBuffer.wrap(new byte[] { 1 })));
        try {
            Client.get("http://localhost:8080/") //
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
        RuntimeException ex = new RuntimeException("boo");
        Server server = createServerAsync(Flowable.error(ex));
        try {
            Client.get("http://localhost:8080/") //
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
        RuntimeException ex = new RuntimeException("boo");
        Server server = createServerAsync( //
                Flowable.just(1, 2, 3) //
                        .concatWith(Flowable.error(ex)) //
                        .map(Serializer.javaIo()::serialize));
        try {
            Client.get("http://localhost:8080/") //
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
        Server server = createServerAsync(Flowable.just(ByteBuffer.wrap(new byte[] { 1 })).repeat());
        try {
            Client.get("http://localhost:8080/") //
                    .build() //
                    .take(20) //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertValueCount(20) //
                    .assertComplete();
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testRange() throws Exception {
        Flowable<ByteBuffer> flowable = Flowable.range(1, 1000).map(Serializer.javaIo()::serialize);
        Server server = createServerAsync(flowable);
        try {
            Client.get("http://localhost:8080/") //
                    .<Integer>deserialized() //
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
    public void testRangeAsync() throws Exception {
        Flowable<ByteBuffer> flowable = Flowable.range(1, 1000).map(Serializer.javaIo()::serialize)
                .observeOn(Schedulers.io());
        Server server = createServerAsync(flowable);
        try {
            Client.get("http://localhost:8080/") //
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
        List<Long> requests = new CopyOnWriteArrayList<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        Flowable<ByteBuffer> flowable = Flowable.range(1, 1000) //
                .map(Serializer.javaIo()::serialize) //
                .doOnRequest(n -> requests.add(n)) //
                .doOnCancel(() -> cancelled.set(true));
        Server server = createServerAsync(flowable);
        try {
            TestSubscriber<Integer> ts = Client.get("http://localhost:8080/") // s
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
        Flowable<ByteBuffer> flowable = Flowable.range(1, 1000).map(Serializer.javaIo()::serialize);
        Server server = createServerAsync(flowable);
        try {
            Flowable<Integer> f = Client.get("http://localhost:8080/") //
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
        Flowable<ByteBuffer> flowable = Flowable.range(1, 1000).map(Serializer.javaIo()::serialize);
        Server server = createServerSync(flowable);
        try {
            Flowable<Integer> f = Client.get("http://localhost:8080/") //
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
    public void testGetWithClient2() throws Exception {
        Server server = createServerAsync(SOURCE);
        try {
            Client.get("http://localhost:8080/") //
                    .method(HttpMethod.GET) //
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
        Server server = createServerAsync(SOURCE);
        try {
            Client.get("http://localhost:8080/") //
                    .method(HttpMethod.POST) //
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
        Server server = createServerFlowableFactoryThrows();
        try {
            Client.get("http://localhost:8080/") //
                    .build() //
                    .reduce(0, (x, bb) -> x + bb.remaining()) //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertNoValues() //
                    .assertError(e -> e.getMessage().startsWith("java.io.IOException: boo"));
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testSimpleGet() throws Exception {
        Server server = createServerAsync(SOURCE);
        try {
            // Start Server
            server.start();
            HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8080/?r=100").openConnection();
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

    @Test
    public void testRequesterNon200ResponseCode() throws Exception {
        Requester r = new Client.Requester("http://localhost/doesNotExist", HttpMethod.GET);
        r.accept(1L, 1L);
    }

    private static Server createServerAsync(Flowable<ByteBuffer> flowable) {
        // Create Server
        Server server = new Server(8080);
        ServletContextHandler context = new ServletContextHandler();
        ServletHolder defaultServ = new ServletHolder("default", HandlerServletAsync.class);
        defaultServ.setInitParameter("resourceBase", System.getProperty("user.dir"));
        defaultServ.setInitParameter("dirAllowed", "true");
        context.addServlet(defaultServ, "/");
        server.setHandler(context);
        HandlerServletAsync.flowable = flowable;
        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return server;
    }

    private static Server createServerFlowableFactoryThrows() {
        // Create Server
        Server server = new Server(8080);
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
        Server server = new Server(8080);
        ServletContextHandler context = new ServletContextHandler();
        ServletHolder defaultServ = new ServletHolder("default", HandlerServletSync.class);
        defaultServ.setInitParameter("resourceBase", System.getProperty("user.dir"));
        defaultServ.setInitParameter("dirAllowed", "true");
        context.addServlet(defaultServ, "/");
        server.setHandler(context);
        HandlerServletSync.flowable = flowable;
        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return server;
    }

    private static BiConsumer<Long, Long> createRequester() {
        return new BiConsumer<Long, Long>() {

            @Override
            public void accept(Long id, Long request) throws Exception {
                HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8080/?id=" + id + "&r=" + request)
                        .openConnection();
                con.setRequestMethod("GET");
                con.setUseCaches(false);
                assertEquals(HttpStatus.OK_200, con.getResponseCode());
            }
        };
    }

}
