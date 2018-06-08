package org.davidmoten.rx2.http;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.davidmoten.rx2.io.internal.ServletHandler;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;

public abstract class FlowableHttpServlet extends HttpServlet {

    private static final long serialVersionUID = 5492424521743846011L;

    private transient ServletHandler handler;

    @Override
    public void init(ServletConfig config) throws ServletException {
        handler = ServletHandler.create();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        Response response;
        try {
            response = respond(req);
        } catch (Throwable e) {
            handler.doGet(Flowable.error(e), req, resp, Schedulers.io(), true);
            return;
        }
        handler.doGet(response.publisher(), req, resp, response.requestScheduler(),
                response.isAsync());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        doGet(req, resp);
    }

    @Override
    public void destroy() {
        handler.close();
        handler = null;
    }

    public abstract Response respond(HttpServletRequest req);

}
