package org.davidmoten.rx2.io;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.reactivex.Flowable;

public final class HandlerServlet extends HttpServlet {

    private static final long serialVersionUID = 4294026368929063494L;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Handler.handleBlocking( //
                Flowable.just(ByteBuffer.wrap(new byte[] { 1, 2, 3 })), //
                req.getInputStream(), //
                resp.getOutputStream());
    }

}
