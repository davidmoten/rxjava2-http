package org.davidmoten.rx2.io;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.davidmoten.rx2.io.Client.Builder;
import org.davidmoten.rx2.io.ssl.Ssl;
import org.eclipse.jetty.server.Server;
import org.junit.Test;

import io.reactivex.Flowable;

public class ClientSslTest {
    
    private static final int PORT = 8443;

    @Test
    public void testAuthenticatedSsl() throws Exception {
        TrustManager[] trustManagers = Ssl.getTrustManagers();
        SSLContext sslContext = Ssl.createTlsSslContext(trustManagers);
        Server server = null;
        try {
            server = Servers.createServerAsyncSsl(Flowable.just(ByteBuffer.wrap(new byte[] { 12 })), "/keyStore.jks",
                    "password", "/trustStore.jks", "password", PORT);
            get(server) //
                    .sslContext(sslContext) //
                    .basicAuth("username", "password") //
                    .build() //
                    .test() //
                    .awaitDone(5, TimeUnit.SECONDS) //
                    .assertValue(bb -> bb.get() == 12) //
                    .assertComplete();
        } finally {
            if (server != null) {
                server.stop();
            }
        }
    }

    private static Builder get(Server server) {
        return Client.get("https://localhost:" + 8443 + "/");
    }

}
