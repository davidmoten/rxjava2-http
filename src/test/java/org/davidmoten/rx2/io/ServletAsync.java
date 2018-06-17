package org.davidmoten.rx2.io;

import java.nio.ByteBuffer;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

import org.davidmoten.rx2.http.FlowableHttpServlet;
import org.davidmoten.rx2.http.Response;

import io.reactivex.Flowable;

@SuppressWarnings("serial")
@WebServlet
public final class ServletAsync extends FlowableHttpServlet {

    public static Flowable<ByteBuffer> flowable = Flowable.empty();

    @Override
    public Response respond(HttpServletRequest req) {
        return Response //
                .from(flowable);
    }

}
