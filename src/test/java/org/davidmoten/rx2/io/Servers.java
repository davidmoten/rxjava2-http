package org.davidmoten.rx2.io;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;

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

    public static Server createServerAsyncSsl(Flowable<ByteBuffer> flowable) {

        Server server = new Server();
        SslContextFactory sslContextFactory = new SslContextFactory("src/test/resources/keyStore.jks");
        sslContextFactory.setKeyStorePassword("password");
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            try (InputStream in = new FileInputStream("src/test/resources/trustStore.jks")) {
                ks.load(in, "password".toCharArray());
            }
            sslContextFactory.setTrustStore(ks);
        } catch (IOException | KeyStoreException | NoSuchAlgorithmException | CertificateException e) {
            throw new RuntimeException(e);
        }

        sslContextFactory.setTrustStorePassword("password");
        SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory,
                org.eclipse.jetty.http.HttpVersion.HTTP_1_1.toString());
        
     // HTTP Configuration
        HttpConfiguration config = new HttpConfiguration();
        config.setSecureScheme("https");
        config.setSecurePort(8443);
        config.setOutputBufferSize(32768);
        config.setRequestHeaderSize(8192);
        config.setResponseHeaderSize(8192);
        config.setSendServerVersion(true);
        config.setSendDateHeader(false);

        // create a https connector
        ServerConnector connector = new ServerConnector(server, sslConnectionFactory, new HttpConnectionFactory(config));
        connector.setPort(8443);
        server.addConnector(connector);

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
