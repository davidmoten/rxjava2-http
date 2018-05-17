package org.davidmoten.rx2.io;

import java.nio.ByteBuffer;

import io.reactivex.Flowable;

public final class HandlerServlet extends FlowableHttpServlet {

    private static final long serialVersionUID = 4294026368929063494L;

    public HandlerServlet() {
        super(createFlowable());
    }

    private static Flowable<ByteBuffer> createFlowable() {
        return Flowable.just(ByteBuffer.wrap(new byte[] { 1, 2, 3 }), ByteBuffer.wrap(new byte[] { 4, 5, 6 }));
    }
    
}
