package org.davidmoten.rx2.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.reactivestreams.Subscription;

import io.reactivex.Flowable;

public final class HandlerServlet extends HttpServlet {

    private static final long serialVersionUID = 4294026368929063494L;

    private final Map<Long, Subscription> map = new ConcurrentHashMap<>();

    @Override
    public void destroy() {
        for (Subscription sub : map.values()) {
            sub.cancel();
        }
        map.clear();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        long id = Long.parseLong(req.getParameter("id"));
        long request = Long.parseLong(req.getParameter("req"));
        Subscription s = map.get(id);
        if (s != null) {
            s.request(request);
        } else {
            throw new ServletException("subscription with id " + id + " not found");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        long id = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        CountDownLatch latch = new CountDownLatch(1);
        Handler.handle( //
                Flowable.just(ByteBuffer.wrap(new byte[] { 1, 2, 3 })), //
                resp.getOutputStream(), //
                () -> latch.countDown(), //
                id, //
                sub -> map.put(id, sub));
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
