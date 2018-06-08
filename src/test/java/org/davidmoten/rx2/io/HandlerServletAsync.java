package org.davidmoten.rx2.io;

import java.nio.ByteBuffer;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

import org.davidmoten.rx2.http.FlowableHttpServlet;
import org.davidmoten.rx2.http.Response;

import io.reactivex.Flowable;

@WebServlet
public final class HandlerServletAsync extends FlowableHttpServlet {

    private static final long serialVersionUID = 4294026368929063494L;

    public static Flowable<ByteBuffer> flowable = Flowable.empty();

    @Override
    public Response respond(HttpServletRequest req) {
        return Response //
                .publisher(flowable) //
                .build();
    }

}
