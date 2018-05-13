package org.davidmoten.rx2.io;

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
import io.reactivex.internal.subscriptions.SubscriptionHelper;
import io.reactivex.internal.util.BackpressureHelper;

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

        private boolean firstTime = true;

        private OutputStream out;

        private InputStream in;

        private Throwable error;
        private volatile boolean finished;

        HttpSubscription(URL url, int bufferSize, Subscriber<? super ByteBuffer> child) {
            this.url = url;
            this.bufferSize = bufferSize;
            this.child = child;
            lazySet(1);
        }

        public void init() {
            try {
                con = url.openConnection();
                out = con.getOutputStream();
                in = con.getInputStream();
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
                try {
                    out.write(Util.toBytes(n));
                } catch (Throwable e) {
                    error = e;
                    finished = true;
                }
                drain();
            }
        }

        private void drain() {
            if (getAndIncrement() == 0) {

            }
        }

        @Override
        public void cancel() {
            // TODO Auto-generated method stub

        }

    }

}
