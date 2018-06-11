package org.davidmoten.rx2.http;

import java.nio.ByteBuffer;

import org.davidmoten.rx2.io.Client;
import org.davidmoten.rx2.io.Servers;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import io.reactivex.Flowable;

@State(Scope.Benchmark)
public class Benchmarks {

    private static final int TOTAL_BYTES = 64 * 1024 * 1024;
    private static final int BYTES_PER_ITEM = 1024;

    @State(Scope.Thread)
    public static class ServerHolder {

        public Server server = null;

        @Param({ "2", "8", "32", "128", "512", "2048", "8192", "32768", "65536", "131072" })
        int bytesPerItem;

        @Setup
        public void setup() throws Exception {
            server = Servers.createServerAsync(
                    Flowable.just(ByteBuffer.wrap(new byte[bytesPerItem])).repeat());
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
                .rebatchRequests(Math.max(TOTAL_BYTES / BYTES_PER_ITEM / 1024, 1)) //
                .take(TOTAL_BYTES / BYTES_PER_ITEM) //
                .count() //
                .blockingGet();
    }

    private static int port(Server server) {
        return ((ServerConnector) server.getConnectors()[0]).getLocalPort();
    }
}
