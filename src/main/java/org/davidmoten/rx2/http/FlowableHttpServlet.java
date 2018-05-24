package org.davidmoten.rx2.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.davidmoten.rx2.io.internal.ServletHandler;
import org.reactivestreams.Publisher;

import io.reactivex.Scheduler;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class FlowableHttpServlet extends HttpServlet {

    private static final long serialVersionUID = 5492424521743846011L;

    private final Scheduler requestScheduler;
    private final Function<? super HttpServletRequest, ? extends Publisher<? extends ByteBuffer>> publisherFactory;
    private final Processing processing;
    private ServletHandler handler;

    public FlowableHttpServlet(
            Function<? super HttpServletRequest, ? extends Publisher<? extends ByteBuffer>> flowableFactory) {
        this(flowableFactory, Processing.ASYNC);
    }

    public FlowableHttpServlet(
            Function<? super HttpServletRequest, ? extends Publisher<? extends ByteBuffer>> flowableFactory,
            Processing processing) {
        this(flowableFactory, processing, Schedulers.io());
    }

    public FlowableHttpServlet(
            Function<? super HttpServletRequest, ? extends Publisher<? extends ByteBuffer>> flowableFactory,
            Processing processing, Scheduler requestScheduler) {
        this.publisherFactory = flowableFactory;
        this.processing = processing;
        this.requestScheduler = requestScheduler;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        handler = ServletHandler.create(requestScheduler);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handler.doGet(publisherFactory, req, resp, processing);
    }

    @Override
    public void destroy() {
        handler.close();
        handler = null;
    }

}
