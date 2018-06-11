package org.davidmoten.rx2.http;

import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.Client;
import org.davidmoten.rx2.io.Servers;
import org.davidmoten.rx2.io.internal.Util;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import io.reactivex.Flowable;

@State(Scope.Benchmark)
public class Benchmarks {

    @State(Scope.Thread)
    public static class ServerHolder {

        Server server = null;

        @Setup
        public void setup() throws Exception {
            server = Servers.createServerAsync(Flowable.just(ByteBuffer.wrap(new byte[8192])).repeat());
            server.start();
        }

        @TearDown(Level.Trial)
        public void shutdown() throws Exception {
            if (server != null) {
                server.stop();
            }
        }

    }

    @Benchmark
    public Long throughput(ServerHolder holder) {
        return Client //
                .get("http://localhost:" + port(holder.server)) //
                .build() //
                .rebatchRequests(16) //
                .take(1000) //
                .count() //
                .blockingGet();
    }

    private static int port(Server server) {
        return ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    }
}
