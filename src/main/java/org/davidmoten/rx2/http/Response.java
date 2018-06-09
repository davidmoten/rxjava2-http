package org.davidmoten.rx2.http;

import java.io.OutputStream;
import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.internal.Util;
import org.reactivestreams.Publisher;

import io.reactivex.Scheduler;
import io.reactivex.functions.BiConsumer;
import io.reactivex.schedulers.Schedulers;

public class Response {

    private final Publisher<? extends ByteBuffer> publisher;

    private final Scheduler requestScheduler;

    private final boolean async;

    private final BiConsumer<? super OutputStream, ? super ByteBuffer> writer;

    Response(Publisher<? extends ByteBuffer> publisher, Scheduler requestScheduler, boolean async,
            BiConsumer<? super OutputStream, ? super ByteBuffer> writer) {
        this.publisher = publisher;
        this.requestScheduler = requestScheduler;
        this.async = async;
        this.writer = writer;
    }

    public Publisher<? extends ByteBuffer> publisher() {
        return publisher;
    }

    public BiConsumer<? super OutputStream, ? super ByteBuffer> writer() {
        return writer;
    }

    public Scheduler requestScheduler() {
        return requestScheduler;
    }

    public boolean isAsync() {
        return async;
    }

    public static Builder publisher(Publisher<? extends ByteBuffer> publisher) {
        return new Builder(publisher);
    }

    public static Response from(Publisher<? extends ByteBuffer> publisher) {
        return publisher(publisher) //
                .async(true) //
                .requestScheduler(Schedulers.io()) //
                .build();
    }

    public static final class Builder {

        private final Publisher<? extends ByteBuffer> publisher;
        private Scheduler requestScheduler = Schedulers.io();
        private boolean async = true;
        private BiConsumer<? super OutputStream, ? super ByteBuffer> writer = Util.defaultWriter();

        Builder(Publisher<? extends ByteBuffer> publisher) {
            this.publisher = publisher;
        }

        public Builder requestScheduler(Scheduler scheduler) {
            this.requestScheduler = scheduler;
            return this;
        }

        public Builder async() {
            return async(true);
        }

        public Builder async(boolean async) {
            this.async = async;
            return this;
        }

        public Builder sync() {
            return async(false);
        }

        public Builder writer(BiConsumer<? super OutputStream, ? super ByteBuffer> writer) {
            this.writer = writer;
            return this;
        }

        public Response build() {
            return new Response(publisher, requestScheduler, async, writer);
        }
    }

}
