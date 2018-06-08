package org.davidmoten.rx2.io;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.davidmoten.rx2.io.Client.Builder;
import org.eclipse.jetty.server.Server;
import org.junit.Test;

import io.reactivex.Flowable;

public class ClientSslTest {

//    @Test
    public void testAuthenticatedSsl() throws Exception {
        System.setProperty("javax.net.debug", "all");
        KeyManager[] keyManagers = Ssl.getKeyManagers();
        TrustManager[] trustManagers = Ssl.getTrustManagers();
        SSLContext sslContext = Ssl.createTlsSslContext(keyManagers, trustManagers);
        Server server = null;
        try {
            server = Servers.createServerAsyncSsl(Flowable.just(ByteBuffer.wrap(new byte[] { 12 })));
            get(server) //
                    .sslContext(sslContext) //
                    .build() //
                    .test() //
                    .awaitDone(5, TimeUnit.SECONDS) //
                    .assertValue(bb -> bb.get() == 12) //
                    .assertComplete();
        } finally {
            if (server != null) {
                server.stop();
            }
            System.setProperty("javax.net.debug", null);
        }

    }

    private static Builder get(Server server) {
        return Client.get("http://localhost:" + 8443 + "/");
    }

}
