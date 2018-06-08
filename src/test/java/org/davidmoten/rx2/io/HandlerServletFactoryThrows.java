package org.davidmoten.rx2.io;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

import org.davidmoten.rx2.http.FlowableHttpServlet;
import org.davidmoten.rx2.http.Response;

@WebServlet
public final class HandlerServletFactoryThrows extends FlowableHttpServlet {

    private static final long serialVersionUID = 4294026368929063494L;

    @Override
    public Response respond(HttpServletRequest req) {
        throw new RuntimeException("boo");
    }

}
