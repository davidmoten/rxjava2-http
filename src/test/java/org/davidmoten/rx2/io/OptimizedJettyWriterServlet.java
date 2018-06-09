package org.davidmoten.rx2.io;

import javax.servlet.http.HttpServletRequest;

import org.davidmoten.rx2.http.FlowableHttpServlet;
import org.davidmoten.rx2.http.Response;

import io.reactivex.Flowable;

@SuppressWarnings("serial")
public class OptimizedJettyWriterServlet extends FlowableHttpServlet {

    @Override
    public Response respond(HttpServletRequest req) {
        return Response //
                .publisher(Flowable.empty()) //
                .writerFactory(OptimizedJettyWriterFactory.INSTANCE) //
                .build();
    }

}
