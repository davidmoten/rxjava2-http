package org.davidmoten.rx2.io;

import static org.junit.Assert.assertEquals;

import java.net.HttpURLConnection;
import java.net.URL;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Test;

import io.reactivex.functions.Consumer;

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
        HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8080/").openConnection();
        con.setInstanceFollowRedirects(false);
        con.setRequestMethod("POST");
        // con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        // con.setRequestProperty("charset", "utf-8");
        // con.setRequestProperty("Content-Length", Integer.toString(postDataLength));
        con.setUseCaches(false);
        Consumer<Long> requester = new Consumer<Long>() {

            @Override
            public void accept(Long t) throws Exception {
                // TODO Auto-generated method stub
                
            }};
        Client.read(con, requester, 0, 8192) //
                .doOnNext(x -> System.out.println(x)) //
                .subscribe();
        assertEquals(HttpStatus.OK_200, con.getResponseCode());

        // Stop Server
        server.stop();
    }

}
