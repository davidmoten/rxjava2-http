package org.davidmoten.rx2.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.Flowable;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.internal.fuseable.SimplePlainQueue;
import io.reactivex.internal.queue.SpscLinkedArrayQueue;
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.internal.util.BackpressureHelper;
import io.reactivex.plugins.RxJavaPlugins;

public class FlowableHttp extends Flowable<ByteBuffer> {

    private URL url;
    private int bufferSize;

    public FlowableHttp(URL url, int bufferSize) {
        this.url = url;
        this.bufferSize = bufferSize;
    }

    @Override
    protected void subscribeActual(Subscriber<? super ByteBuffer> subscriber) {
        HttpSubscription subscription = new HttpSubscription(url, bufferSize, subscriber);
        subscriber.onSubscribe(subscription);
        subscription.init();
    }

    private static final class HttpSubscription extends AtomicInteger implements Subscription {

        private static final long serialVersionUID = 5917186677331992560L;

        private final URL url;
        private final int bufferSize;
        private final Subscriber<? super ByteBuffer> child;
        private URLConnection con;
        private final AtomicLong requested = new AtomicLong();
        private final SimplePlainQueue<ByteBuffer> queue = new SpscLinkedArrayQueue<>(16);

        private boolean firstTime = true;

        private OutputStream out;

        private InputStream in;

        private Throwable error;
        private volatile boolean finished;
        private long emitted;

        private final int preRequest;

        private volatile boolean cancelled;

        HttpSubscription(URL url, int preRequest, int bufferSize,
                Subscriber<? super ByteBuffer> child) {
            this.url = url;
            this.preRequest = preRequest;
            this.bufferSize = bufferSize;
            this.child = child;
            lazySet(1);
        }

        public void init() {
            try {
                con = url.openConnection();
                out = con.getOutputStream();
                in = con.getInputStream();
                requestViaOutputStream(preRequest);
                set(0);
                drain();
            } catch (Throwable e) {
                Exceptions.throwIfFatal(e);
                child.onError(e);
                return;
            }
        }

        @Override
        public void request(long n) {
            if (SubscriptionHelper.validate(n)) {
                BackpressureHelper.add(requested, n);
                requestViaOutputStream(n);
                drain();
            }
        }

        private void requestViaOutputStream(long n) {
            try {
                out.write(Util.toBytes(n));
            } catch (Throwable e) {
                error = e;
                finished = true;
            }
        }

        private void drain() {
            if (getAndIncrement() == 0) {
                int missed = 1;
                while (true) {
                    if (tryCancelled()) {
                        return;
                    }
                    long r = requested.get();
                    long e = emitted;
                    while (e != r) {
                        boolean d = finished;
                        ByteBuffer bb = queue.poll();
                        if (bb == null) {
                            if (d) {
                                Throwable err = error;
                                if (err != null) {
                                    error = null;
                                    child.onError(err);
                                } else {
                                    child.onComplete();
                                }
                            } else {
                                // read some more
                                byte[] b = new byte[bufferSize];
                                try {
                                    int n = in.read(b);
                                    if (n == -1) {
                                        close(in);
                                        close(out);
                                        finished = true;
                                    } else {
                                        queue.offer(bb);
                                    }
                                } catch (Throwable e1) {
                                    Exceptions.throwIfFatal(e1);
                                    close(in);
                                    close(out);
                                    error = e1;
                                    finished = true;
                                }
                            }
                        } else {
                            child.onNext(bb);
                            e++;
                            if (preRequest > 0 && e % preRequest == 0) {
                                // prefetch
                                requestViaOutputStream(preRequest);
                            }
                        }
                        if (tryCancelled()) {
                            return;
                        }
                    }
                    emitted = e;
                    missed = addAndGet(-missed);
                    if (missed == 0) {
                        return;
                    }

                }
            }
        }

        private boolean tryCancelled() {
            if (cancelled) {
                close(in);
                close(out);
                queue.clear();
                return true;
            } else {
                return false;
            }
        }

        private void close(Closeable c) {
            if (c != null) {
                try {
                    c.close();
                } catch (IOException e) {
                    RxJavaPlugins.onError(e);
                }
            }

        }

        @Override
        public void cancel() {
            cancelled = true;

        }

    }

}
