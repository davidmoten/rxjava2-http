package org.davidmoten.rx2.io;

import static org.junit.Assert.assertEquals;

import java.net.HttpURLConnection;
import java.net.URL;

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
        // Create Server
        Server server = new Server(8080);
        ServletContextHandler context = new ServletContextHandler();
        ServletHolder defaultServ = new ServletHolder("default", HandlerServlet.class);
        defaultServ.setInitParameter("resourceBase", System.getProperty("user.dir"));
        defaultServ.setInitParameter("dirAllowed", "true");
        context.addServlet(defaultServ, "/");
        server.setHandler(context);

        // Start Server
        server.start();

        // Test GET
        HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8080/r=100").openConnection();
        con.setRequestMethod("GET");
        con.setUseCaches(false);
        BiConsumer<Long, Long> requester = createRequester();
        Client.read(Single.just(con.getInputStream()), requester, 0, 8192) //
                .doOnNext(x -> System.out.println(x)) //
                .subscribe();
        assertEquals(HttpStatus.OK_200, con.getResponseCode());

        // Stop Server
        server.stop();
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
