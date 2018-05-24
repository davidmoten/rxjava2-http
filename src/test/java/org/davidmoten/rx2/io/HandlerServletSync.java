package org.davidmoten.rx2.io;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.davidmoten.rx2.http.FlowableHttpServlet;
import org.davidmoten.rx2.http.Processing;

import io.reactivex.Flowable;

@WebServlet(asyncSupported = true)
public final class HandlerServletSync extends FlowableHttpServlet {

    private static final long serialVersionUID = 4294026368929063494L;

    public static Flowable<ByteBuffer> flowable = Flowable.empty();

    public HandlerServletSync() {
        super(req -> flowable, Processing.SYNC);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

}
