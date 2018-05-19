package org.davidmoten.rx2.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Test;

import io.reactivex.Single;
import io.reactivex.functions.BiConsumer;

public class ClientTest {

    @Test
    public void test() throws Exception {
        Server server = createServer();
        try {
            // Test GET
            HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8080/?r=100")
                    .openConnection();
            con.setRequestMethod("GET");
            con.setUseCaches(false);
            BiConsumer<Long, Long> requester = createRequester();
            Client.read(Single.just(con.getInputStream()), requester, 0, 8192, true) //
                    .doOnNext(x -> System.out.println(x)) //
                    .reduce(0, (x, bb) -> x + bb.remaining()) //
                    .test() //
                    .assertValue(3 + 4) //
                    .assertComplete();
            assertEquals(HttpStatus.OK_200, con.getResponseCode());
        } finally {
            // Stop Server
            server.stop();
        }
    }

    @Test
    public void testSimpleGet() throws Exception {
        Server server = createServer();
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
            assertEquals(23, count);
        } finally {
            server.stop();
        }
    }

    private static Server createServer() {
        // Create Server
        Server server = new Server(8080);
        ServletContextHandler context = new ServletContextHandler();
        ServletHolder defaultServ = new ServletHolder("default", HandlerServlet.class);
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
