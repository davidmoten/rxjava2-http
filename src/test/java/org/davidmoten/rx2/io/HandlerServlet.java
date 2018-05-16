package org.davidmoten.rx2.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;

public final class HandlerServlet extends HttpServlet {

    private static final long serialVersionUID = 4294026368929063494L;

    private final Flowable<ByteBuffer> flowable = Flowable
            .just(ByteBuffer.wrap(new byte[] { 1, 2, 3 }), ByteBuffer.wrap(new byte[] { 4, 5, 6 }));

    private final Random random = new Random();

    private final Map<Long, Subscription> map = new ConcurrentHashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String idString = req.getParameter("id");
        if (idString == null) {
            final long r = getRequest(req);
            handleStream(resp.getOutputStream(), r);
        } else {
            long id = Long.parseLong(idString);
            long request = Long.parseLong(req.getParameter("r"));
            handleRequest(id, request);
        }
    }

    private void handleStream(OutputStream out, long request) {
        CountDownLatch latch = new CountDownLatch(1);
        Runnable completion = () -> latch.countDown();
        long id = random.nextLong();
        Consumer<Subscription> subscription = sub -> map.put(id, sub);
        Handler.handle(flowable, Single.just(out), completion, id, subscription);
        if (request > 0) {
            map.get(id).request(request);
        }
    }

    private void handleRequest(long id, long request) {
        Subscription s = map.get(id);
        if (s != null) {
            s.request(request);
        }
    }

    @Override
    public void destroy() {
        for (Subscription sub : map.values()) {
            sub.cancel();
        }
        map.clear();
    }

    private static long getRequest(HttpServletRequest req) {
        String rString = req.getParameter("r");
        final long r;
        if (rString == null) {
            r = Long.parseLong(rString);
        } else {
            r = 0;
        }
        return r;
    }
}
