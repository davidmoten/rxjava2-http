package org.davidmoten.rx2.io;

import java.nio.ByteBuffer;

import org.davidmoten.rx2.http.FlowableHttpServlet;

import io.reactivex.Flowable;

public final class HandlerServlet extends FlowableHttpServlet {

    private static final long serialVersionUID = 4294026368929063494L;

    public static Flowable<ByteBuffer> flowable = Flowable.empty();
    
    public HandlerServlet() {
        super(flowable);
    }

}
