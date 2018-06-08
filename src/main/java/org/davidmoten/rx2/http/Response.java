package org.davidmoten.rx2.http;

import java.nio.ByteBuffer;

import org.reactivestreams.Publisher;

import io.reactivex.Scheduler;
import io.reactivex.schedulers.Schedulers;

public class Response {

    private final Publisher<? extends ByteBuffer> publisher;

    private final Scheduler requestScheduler;

    private final boolean async;

    Response(Publisher<? extends ByteBuffer> publisher, Scheduler requestScheduler, boolean async) {
        this.publisher = publisher;
        this.requestScheduler = requestScheduler;
        this.async = async;
    }

    public Publisher<? extends ByteBuffer> publisher() {
        return publisher;
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
        return publisher(publisher).build();
    }

    public static final class Builder {

        private final Publisher<? extends ByteBuffer> publisher;
        private Scheduler requestScheduler = Schedulers.io();
        private boolean async = true;

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
        
        public Response build() {
            return new Response(publisher, requestScheduler, async);
        }
    }

}
