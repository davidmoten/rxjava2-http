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
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class FlowableHttpServlet extends HttpServlet {

    private static final long serialVersionUID = 5492424521743846011L;

    private final Scheduler requestScheduler;
    private final Function<? super HttpServletRequest, ? extends Flowable<? extends ByteBuffer>> flowableFactory;
    private ServletHandler handler;

    public FlowableHttpServlet(
            Function<? super HttpServletRequest, ? extends Flowable<? extends ByteBuffer>> flowableFactory) {
        this(flowableFactory, Schedulers.io());
    }

    public FlowableHttpServlet(
            Function<? super HttpServletRequest, ? extends Flowable<? extends ByteBuffer>> flowableFactory,
            Scheduler requestScheduler) {
        this.flowableFactory = flowableFactory;
        this.requestScheduler = requestScheduler;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        handler = ServletHandler.create(requestScheduler);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Flowable<? extends ByteBuffer> flowable;
        try {
            flowable = flowableFactory.apply(req);
        } catch (Throwable e) {
            handler.onError(e, req, resp);
            return;
        }
        handler.doGet(flowable, req, resp);
    }

    @Override
    public void destroy() {
        handler.destroy();
        handler = null;
    }

}
