package org.davidmoten.rx2.io;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;

import org.davidmoten.rx2.http.FlowableHttpServlet;

@WebServlet(asyncSupported = true)
public final class HandlerServletFactoryThrows extends FlowableHttpServlet {

    private static final long serialVersionUID = 4294026368929063494L;

    public HandlerServletFactoryThrows() {
        super(req -> {
            throw new IOException("boo");
        });
    }

}
