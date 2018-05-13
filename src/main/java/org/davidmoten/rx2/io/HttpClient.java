package org.davidmoten.rx2.io;

import java.net.URL;
import java.nio.ByteBuffer;

import io.reactivex.Flowable;

public class HttpClient {

    private final URL url;
    private final int bufferSize;

    HttpClient(URL url, int bufferSize) {
        this.url = url;
        this.bufferSize = bufferSize;
    }
    
    public Flowable<ByteBuffer> post() {
        
    }
    
    
}
