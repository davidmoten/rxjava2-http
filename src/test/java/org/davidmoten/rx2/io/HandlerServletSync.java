package org.davidmoten.rx2.io;

import java.nio.ByteBuffer;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

import org.davidmoten.rx2.http.FlowableHttpServlet;
import org.davidmoten.rx2.http.Response;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;

@SuppressWarnings("serial")
@WebServlet
public final class HandlerServletSync extends FlowableHttpServlet {

    public static Flowable<ByteBuffer> flowable = Flowable.empty();

    @Override
    public Response respond(HttpServletRequest req) {
        return Response //
                .publisher(flowable) //
                .requestScheduler(Schedulers.io()) //
                .async(false) //
                .build();
    }

}
