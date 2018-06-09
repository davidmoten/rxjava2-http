package org.davidmoten.rx2.io.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.davidmoten.rx2.http.Response;
import org.davidmoten.rx2.http.WriterFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;

import com.github.davidmoten.guavamini.annotations.VisibleForTesting;

import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public final class ServletHandler {

    private final Random random = new Random();

    private final Map<Long, Subscription> map = new ConcurrentHashMap<>();

    public static ServletHandler create() {
        return new ServletHandler();
    }

    private ServletHandler() {
    }

    public void doGet(Callable<Response> responseProvider, HttpServletRequest req,
            HttpServletResponse resp) throws ServletException, IOException {
        String idString = req.getParameter("id");
        if (idString == null) {
            final long r = getRequest(req);
            resp.setContentType("application/octet-stream");
            Response response;
            try {
                response = responseProvider.call();
            } catch (Throwable e) {
                // default to blocking
                handleStreamBlocking(Flowable.error(e), resp.getOutputStream(), Schedulers.io(), r,
                        WriterFactory.DEFAULT);
                return;
            }
            if (!response.isAsync() || !req.isAsyncSupported()) {
                handleStreamBlocking(response.publisher(), resp.getOutputStream(),
                        response.requestScheduler(), r, response.writerFactory());
            } else {
                AsyncContext asyncContext = req.startAsync();
                // prevent timeout because streams can be long-running
                // TODO make configurable?
                asyncContext.setTimeout(0);
                handleStreamNonBlocking(response.publisher(),
                        asyncContext.getResponse().getOutputStream(), response.requestScheduler(),
                        r, asyncContext, response.writerFactory());
            }
        } else {
            long id = Long.parseLong(idString);
            long request = Long.parseLong(req.getParameter("r"));
            handleRequest(id, request);
        }
    }

    private void handleStreamBlocking(Publisher<? extends ByteBuffer> publisher, OutputStream out,
            Scheduler requestScheduler, long request,
            WriterFactory writerFactory) {
        CountDownLatch latch = new CountDownLatch(1);
        long id = nextId(random);
        Runnable done = () -> {
            map.remove(id);
            latch.countDown();
        };
        handleStream(publisher, out, requestScheduler, request, id, done, writerFactory);
        // TODO configure max wait time or allow requester to decide?
        waitFor(latch);
    }

    @VisibleForTesting
    static void waitFor(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            // do nothing
        }
    }

    private void handleStreamNonBlocking(Publisher<? extends ByteBuffer> publisher,
            OutputStream out, Scheduler requestScheduler, long request, AsyncContext asyncContext,
            WriterFactory writerFactory) {
        long id = nextId(random);
        Runnable done = () -> {
            map.remove(id);
            asyncContext.complete();
        };
        handleStream(publisher, out, requestScheduler, request, id, done, writerFactory);
    }

    private void handleStream(Publisher<? extends ByteBuffer> publisher, OutputStream out,
            Scheduler requestScheduler, long request, long id, Runnable completion,
            WriterFactory writerFactory) {
        Consumer<Subscription> subscription = sub -> map.put(id, sub);
        Server.handle(publisher, Single.just(out), completion, id, requestScheduler, subscription,
                writerFactory);
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

    @VisibleForTesting
    static long nextId(Random random) {
        // id == 0 has special meaning in client so lets not use that
        long id;
        do {
            id = random.nextLong();
        } while (id == 0);
        return id;
    }

}
