package org.davidmoten.rx2.io;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Test;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.BiConsumer;

public class ClientTest {

    private static final Flowable<ByteBuffer> SOURCE = Flowable.just(
            ByteBuffer.wrap(new byte[] { 1, 2, 3 }), ByteBuffer.wrap(new byte[] { 4, 5, 6, 7 }));

    @Test
    public void testGetWithClient() throws Exception {
        Server server = createServer(SOURCE);
        try {
            HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8080/?r=100")
                    .openConnection();
            con.setRequestMethod("GET");
            con.setUseCaches(false);
            BiConsumer<Long, Long> requester = createRequester();
            Client.read(Single.just(con.getInputStream()), requester) //
                    .doOnNext(x -> System.out.println(x)) //
                    .reduce(0, (x, bb) -> x + bb.remaining()) //
                    .test() //
                    .awaitDone(10, TimeUnit.SECONDS) //
                    .assertValue(3 + 4) //
                    .assertComplete();
            assertEquals(HttpStatus.OK_200, con.getResponseCode());
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testGetEmptyStream() throws Exception {
        Server server = createServer(Flowable.empty());
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
        Server server = createServer( //
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
    public void testCancel() throws Exception {
        Server server = createServer(Flowable.just(ByteBuffer.wrap(new byte[] { 1 })).repeat());
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
        Flowable<ByteBuffer> flowable = Flowable.range(1, 1000)
                .map(DefaultSerializer.instance()::serialize);
        Server server = createServer(flowable);
        try {
            Client.get("http://localhost:8080/") // s
                    .serializer(DefaultSerializer.<Integer>instance()) //
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
    public void testGetWithClientAbbreviated() throws Exception {
        Server server = createServer(SOURCE);
        try {
            Client.get("http://localhost:8080/", 100) //
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
    public void testSimpleGet() throws Exception {
        Server server = createServer(SOURCE);
        try {
            // Start Server
            server.start();
            HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8080/?r=100")
                    .openConnection();
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

    private static Server createServer(Flowable<ByteBuffer> flowable) {
        // Create Server
        Server server = new Server(8080);
        ServletContextHandler context = new ServletContextHandler();
        ServletHolder defaultServ = new ServletHolder("default", HandlerServlet.class);
        defaultServ.setInitParameter("resourceBase", System.getProperty("user.dir"));
        defaultServ.setInitParameter("dirAllowed", "true");
        context.addServlet(defaultServ, "/");
        server.setHandler(context);
        HandlerServlet.flowable = flowable;
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
                HttpURLConnection con = (HttpURLConnection) new URL(
                        "http://localhost:8080/?id=" + id + "&r=" + request).openConnection();
                con.setRequestMethod("GET");
                con.setUseCaches(false);
                assertEquals(HttpStatus.OK_200, con.getResponseCode());
            }
        };
    }

}
