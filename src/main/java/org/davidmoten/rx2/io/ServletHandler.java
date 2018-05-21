package org.davidmoten.rx2.io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public final class ServletHandler {

    private final Random random = new Random();

    private final Map<Long, Subscription> map = new ConcurrentHashMap<>();

    private final Flowable<ByteBuffer> flowable;

    private final Scheduler requestScheduler;

    public static ServletHandler create(Flowable<ByteBuffer> flowable) {
        return create(flowable, Schedulers.io());
    }

    public static ServletHandler create(Flowable<ByteBuffer> flowable, Scheduler requestScheduler) {
        return new ServletHandler(flowable, requestScheduler);
    }

    private ServletHandler(Flowable<ByteBuffer> flowable, Scheduler requestScheduler) {
        this.flowable = flowable;
        this.requestScheduler = requestScheduler;
    }

    public void doGet(HttpServletRequest req, HttpServletResponse resp)
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
        long id = random.nextLong();
        Runnable done = () -> {
            map.remove(id);
            latch.countDown();
        };
        Consumer<Subscription> subscription = sub -> map.put(id, sub);
        Server.handle(flowable, Single.just(out), done, id, requestScheduler, subscription);
        if (request > 0) {
            Subscription sub = map.get(id);
            if (sub != null) {
                sub.request(request);
            }
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    private void handleRequest(long id, long request) {
        Subscription s = map.get(id);
        if (s != null) {
            if (request > 0) {
                s.request(request);
            } else if (request < 0) {
                s.cancel();
            }
        }
    }

    private static long getRequest(HttpServletRequest req) {
        String rString = req.getParameter("r");
        final long r;
        if (rString != null) {
            r = Long.parseLong(rString);
        } else {
            r = 0;
        }
        return r;
    }

    public void destroy() {
        for (Subscription sub : map.values()) {
            sub.cancel();
        }
        map.clear();
    }
}
