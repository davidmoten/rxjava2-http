package org.davidmoten.rx2.io;

import static org.junit.Assert.assertEquals;

import java.net.HttpURLConnection;
import java.net.URL;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Test;

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
        HttpURLConnection http = (HttpURLConnection) new URL("http://localhost:8080/")
                .openConnection();
        http.connect();
        assertEquals(HttpStatus.OK_200, http.getResponseCode());

        // Stop Server
        server.stop();
    }

}
