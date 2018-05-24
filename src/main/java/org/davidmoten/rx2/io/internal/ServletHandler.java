package org.davidmoten.rx2.io.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.davidmoten.rx2.http.Processing;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;

public final class ServletHandler {

    private final Random random = new Random();

    private final Map<Long, Subscription> map = new ConcurrentHashMap<>();

    private final Scheduler requestScheduler;

    public static ServletHandler create(Scheduler requestScheduler) {
        return new ServletHandler(requestScheduler);
    }

    private ServletHandler(Scheduler requestScheduler) {
        this.requestScheduler = requestScheduler;
    }

    public void doGet(Function<? super HttpServletRequest, ? extends Publisher<? extends ByteBuffer>> publisherFactory,
            HttpServletRequest req, HttpServletResponse resp, Processing processing)
            throws ServletException, IOException {
        Publisher<? extends ByteBuffer> publisher;
        try {
            publisher = publisherFactory.apply(req);
        } catch (Throwable e) {
            doGet(Flowable.error(e), req, resp, processing);
            return;
        }
        doGet(publisher, req, resp, processing);
    }

    public void doGet(Publisher<? extends ByteBuffer> publisher, HttpServletRequest req, HttpServletResponse resp,
            Processing processing) throws ServletException, IOException {
        String idString = req.getParameter("id");
        if (idString == null) {
            final long r = getRequest(req);
            if (processing == Processing.SYNC || !req.isAsyncSupported()) {
                System.out.println("blocking=============");
                handleStreamBlocking(publisher, resp.getOutputStream(), r);
            } else {
                AsyncContext asyncContext = req.startAsync();
                handleStreamNonBlocking(publisher, asyncContext.getResponse().getOutputStream(), r, asyncContext);
            }
        } else {
            long id = Long.parseLong(idString);
            long request = Long.parseLong(req.getParameter("r"));
            handleRequest(id, request);
        }
    }

    private void handleStreamBlocking(Publisher<? extends ByteBuffer> publisher, OutputStream out, long request) {
        CountDownLatch latch = new CountDownLatch(1);
        long id = random.nextLong();
        Runnable done = () -> {
            map.remove(id);
            latch.countDown();
        };
        handleStream(publisher, out, request, id, done);
        // TODO configure max wait time or allow requester to decide?
        try {
            latch.await();
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    private void handleStreamNonBlocking(Publisher<? extends ByteBuffer> publisher, OutputStream out, long request,
            AsyncContext asyncContext) {
        long id = random.nextLong();
        Runnable done = () -> {
            map.remove(id);
            asyncContext.complete();
        };
        handleStream(publisher, out, request, id, done);
    }

    private void handleStream(Publisher<? extends ByteBuffer> publisher, OutputStream out, long request, long id,
            Runnable done) {
        Consumer<Subscription> subscription = sub -> map.put(id, sub);
        Server.handle(publisher, Single.just(out), done, id, requestScheduler, subscription);
        if (request > 0) {
            Subscription sub = map.get(id);
            if (sub != null) {
                sub.request(request);
            }
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

    public void close() {
        for (Subscription sub : map.values()) {
            sub.cancel();
        }
        map.clear();
    }

}
