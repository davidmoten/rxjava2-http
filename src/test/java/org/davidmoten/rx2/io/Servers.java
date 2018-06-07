package org.davidmoten.rx2.io;

import java.nio.ByteBuffer;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import io.reactivex.Flowable;

public final class Servers {
    
    public static Server createServerAsync(Flowable<ByteBuffer> flowable) {
        Server server = new Server(0);
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
}
