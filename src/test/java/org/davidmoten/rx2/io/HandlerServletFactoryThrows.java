package org.davidmoten.rx2.io;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

import org.davidmoten.rx2.http.FlowableHttpServlet;
import org.davidmoten.rx2.http.Response;

@SuppressWarnings("serial")
@WebServlet
public final class HandlerServletFactoryThrows extends FlowableHttpServlet {

    @Override
    public Response respond(HttpServletRequest req) {
        throw new RuntimeException("boo");
    }

}
