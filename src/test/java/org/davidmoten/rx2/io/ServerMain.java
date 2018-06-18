package org.davidmoten.rx2.io;

import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.internal.Util;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import io.reactivex.Flowable;

public class ServerMain {

    public static void main(String[] args) throws Exception {
        Flowable<ByteBuffer> flowable = Flowable //
                .rangeLong(1, Long.MAX_VALUE) //
                .map(x -> ByteBuffer.wrap(Util.toBytes(x)));
        Server server = new Server(8080);
        ServletContextHandler context = new ServletContextHandler();
        ServletHolder defaultServ = new ServletHolder("default", ServletAsync.class);
        defaultServ.setInitParameter("resourceBase", System.getProperty("user.dir"));
        defaultServ.setInitParameter("dirAllowed", "true");
        context.addServlet(defaultServ, "/");
        server.setHandler(context);
        ServletAsync.flowable = flowable;
        server.start();
    }

}
