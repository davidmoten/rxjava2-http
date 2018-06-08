package org.davidmoten.rx2.io;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

public class Ssl {

    private static final String PASSWORD = "password";

    public static KeyManager[] getKeyManagers() throws KeyStoreException, IOException, NoSuchAlgorithmException,
            CertificateException, UnrecoverableKeyException {
        InputStream keyStore = Ssl.class.getResourceAsStream("/keyStore.jks");
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(keyStore, PASSWORD.toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, PASSWORD.toCharArray());
        KeyManager[] keyManagers = kmf.getKeyManagers();
        return keyManagers;
    }

    static TrustManager[] getTrustManagers() {
        InputStream trustStore = Ssl.class.getResourceAsStream("/trustStore.jks");
        TrustManager trustManager = new ExtendedTrustManager(trustStore, PASSWORD.toCharArray(), false);
        TrustManager[] trustManagers = new TrustManager[] { trustManager };
        return trustManagers;
    }

    static SSLContext createTlsSslContext(KeyManager[] keyManagers, TrustManager[] trustManagers)
            throws NoSuchAlgorithmException, KeyManagementException {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, new java.security.SecureRandom());
        return sslContext;
    }
}
