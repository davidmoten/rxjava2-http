package org.davidmoten.rx2.http;

import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.WriterFactory;
import org.reactivestreams.Publisher;

import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

public class Response {

    private final Publisher<? extends ByteBuffer> publisher;

    private final Scheduler requestScheduler;

    private final boolean async;

    private final WriterFactory writerFactory;

    Response(Publisher<? extends ByteBuffer> publisher, Scheduler requestScheduler, boolean async,
            WriterFactory writerFactory) {
        this.publisher = publisher;
        this.requestScheduler = requestScheduler;
        this.async = async;
        this.writerFactory = writerFactory;
    }

    public Publisher<? extends ByteBuffer> publisher() {
        return publisher;
    }

    public WriterFactory writerFactory() {
        return writerFactory;
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
        private WriterFactory writerFactory = WriterFactory.DEFAULT;

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

        public Builder writerFactory(WriterFactory writerFactory) {
            this.writerFactory = writerFactory;
            return this;
        }

        public Response build() {
            return new Response(publisher, requestScheduler, async, writerFactory);
        }
    }

}
