package org.davidmoten.rx2.io;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.reactivex.Flowable;

public class FlowableHttpServlet extends HttpServlet {
    
    private static final long serialVersionUID = 5492424521743846011L;
    
    private final Flowable<ByteBuffer> flowable;
    private ServletHandler handler;
    
    public FlowableHttpServlet(Flowable<ByteBuffer> flowable) {
        this.flowable = flowable;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        handler = ServletHandler.create(flowable);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handler.doGet(req, resp);
    }

    @Override
    public void destroy() {
        handler.destroy();
        handler = null;
    }

}
