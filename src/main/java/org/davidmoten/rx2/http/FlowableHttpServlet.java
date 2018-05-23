package org.davidmoten.rx2.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.davidmoten.rx2.io.internal.ServletHandler;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

public class FlowableHttpServlet extends HttpServlet {

    private static final long serialVersionUID = 5492424521743846011L;

    private final Flowable<ByteBuffer> flowable;
    private final Scheduler requestScheduler;
    private ServletHandler handler;

    public FlowableHttpServlet(Flowable<ByteBuffer> flowable) {
        this(flowable, Schedulers.io());
    }

    public FlowableHttpServlet(Flowable<ByteBuffer> flowable, Scheduler requestScheduler) {
        this.flowable = flowable;
        this.requestScheduler = requestScheduler;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        handler = ServletHandler.create(flowable, requestScheduler);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        handler.doGet(req, resp);
    }

    @Override
    public void destroy() {
        handler.destroy();
        handler = null;
    }

}
