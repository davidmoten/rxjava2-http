# rxjava2-http
<a href="https://travis-ci.org/davidmoten/rxjava2-http"><img src="https://travis-ci.org/davidmoten/rxjava2-http.svg"/></a><br/>
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.davidmoten/rxjava2-http/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.github.davidmoten/rxjava2-http)<br/>
[![codecov](https://codecov.io/gh/davidmoten/rxjava2-http/branch/master/graph/badge.svg)](https://codecov.io/gh/davidmoten/rxjava2-http)

Transmit RxJava2 Flowable over http (with non-blocking backpressure).

Status: *pre-alpha* (in development). First release scheduled for late June 2018.

## Features
* Apply non-blocking backpressure to streams over networks
* Use whatever serialization library you want (the core supports `Flowable<ByteBuffer>`)
* Supports plain HTTP/HTTPS service (no firewall troubles with WebSockets)
* Uses Servlet 3.1+ asynchronous processing (by default)

<img src="src/docs/marble-diagram.png?raw=true" />

## Getting started

### Maven dependency
```xml
<dependency>
  <groupId>com.github.davidmoten</groupId>
  <artifactId>rxjava2-http</artifactId>
  <version>VERSION_HERE</version>
</dependency>
```

### Create servlet
The servlet below exposes the `Flowable.range(1, 1000)` stream across HTTP using standard java serialization:

```java
@WebServlet(urlPatterns={"/stream"})
public class StreamServlet extends FlowableHttpServlet {
      
  @Override
  public Response respond(HttpServletRequest req) {
    return Response.from(  
      Flowable
        .range(1,1000)
        .map(Serializer.javaIo()::serialize)));
    }
}
```

The default behaviour is to schedule requests on `Schedulers.io()` and to handle requests asynchronously. These aspects can be configured via other methods in the `Response` builder. See [Throughput](#throughput) section for more details.

Bear in mind that each `ByteBuffer` in the server flowable will be reconstituted as is on the client side and you will want to ensure that the client can allocate byte arrays for each item. For example, mobile platforms like Android have quite low max heap sizes so that if the client is one of those platforms you will need to chunk up the data appropriately. (This may be a use-case for supporting [Nested Flowables](#nested-flowables), let me know if you need it).

### Create client
Assuming the servlet above is listening on `http://localhost:8080/stream`, this is how you access it over HTTP:

```java
Flowable<Integer> numbers = 
  Client
   .get("http://localhost:8080/stream") //HTTP GET
   .deserialized(); // use standard java serialization
```
More client options are available. Here is an example:

```java
Flowable<Integer> numbers = 
  Client
    .post("https://localhost:8080/stream") // HTTP POST
    .connectTimeoutMs(3000) 
    .readTimeoutMs(30000) 
    .proxy(host, port)
    .sslContext(sslContext)
    .transform(con -> con.setFollowRedirects(true))
    .basicAuth("username", "password")
    .deserializer(deserializer) // used for deserialization
    .rebatchRequests(128); // necessary to enable backpressure over the network without blocking calls
```

Note that if you need proxy authentication as well then use System properties or set an `Authenticator`:

```java
Authenticator authenticator = new Authenticator() {
    public PasswordAuthentication getPasswordAuthentication() {
      return (new PasswordAuthentication("user",
        "password".toCharArray()));
    }
};
Authenticator.setDefault(authenticator);
```

### SSL/TLS

The unit test [`ClientSslTest.java`](src/test/java/org/davidmoten/rx2/io/ClientSslTest.java) has a round-trip test using Jetty, TLS 1.2, keystore, truststore and basic authentication. Check it out if you are having trouble.  

Here's an example:

```java
Flowable<Integer> numbers = 
  Client.get("https://localhost:8443")
    .sslContext(sslContext)
    .basicAuth(username, password)
    .build();
```

### Serializers

`Serializer.javaIo()` can be used to serialize classes that implement `Serializable`. It is much slower than products like *Kryo* or indeed if you have the time, custom serialization.

## Good practices

### Backpressure
To ensure backpressure is applied over the network (so operating system IO buffers don't fill and block threads) it's a good idea to request data in batches (and to request more before the buffer is exhausted to lessen the effect of request overhead):

* apply `rebatchRequests` to the client-side Flowable
* [*rxjava2-extras*](https://github.com/davidmoten/rxjava2-extras) has a number of request manipulating operators (`minRequest`, `maxRequest` and another version of [`rebatchRequests`](https://github.com/davidmoten/rxjava2-extras#rebatchrequests) with different features)

### Quiet streams
Note that a long running quiet source Flowable over http(s) is indistinguishable from a chopped connection (by a firewall for instance). To avoid this:

* regularly cancel and reconnect to the stream

OR

* include a heartbeat emission in the Flowable which you filter out on the client side

OR

* put a `timeout` operator on the Flowable on the client side and `retry`

The heartbeat option is especially good if a reconnect could mean missed emissions.

### Orphan streams on the server

It's also a good idea to:

*  put a `timeout` operator on the server Flowable in case a client leaves (or is killed) without cancelling

This goes for any server Flowable, even one that is normally of very short duration. This is because the subscription is retained in a global map until cancellation and will retain some memory. Note that under a lot of GC pressure a container may choose to destroy a servlet (and run `init` again when another call to that servlet happens). In this circumstance `FlowableHttpServlet` is designed to cancel all outstanding subscriptions and release the mentioned map for gc. 

### Blocking

The Flowable returned by the `Client` is blocking in nature (it's reading across a network and can block while doing that). As a consequence make sure you don't run
it on `Schedulers.computation` (that is one Scheduler we should never block) but rather use `Schedulers.io()` or `Schedulers.from(executor)`.

A quick example of what **NOT** to do is this:

```java
//run the Client call every 10 seconds
Flowable
  .interval(10, TimeUnit.SECONDS) // Not Good Because uses Scheduler.computation()
  .flatMap(n -> 
      Client
        .get("http://localhost:8080/stream")
        .deserialized())
  .doOnNext(System.out::println)
  .subscribe(...);
```        
Instead you should use an explicit `Scheduler` other than `computation`:
```
//run the Client call every 10 seconds on io()
Flowable
  .interval(10, TimeUnit.SECONDS, Schedulers.io()) // Good
  .flatMap(n -> 
      Client
        .get("http://localhost:8080/stream")
        .deserialized())
  .doOnNext(System.out::println)
  .subscribe(...);
```

## Design
WebSockets is a natural for this but can be blocked by corporate firewalls (and can be problematic with HTTP/2) so this library starts with support for HTTP 1.1. 

We want API support for these actions:

* *subscribe*
* *request*
* *cancel*

Support is provided via these URL paths

Path | Method | Action | Returns
--- | --- | --- | --
`/`   | GET | subscribe with no request | Stream ID then binary stream
`/?r=REQUEST`|GET | subscribe with initial request | Stream ID then binary stream
`/?id=ID&r=REQUEST` |GET| request more from given stream | nothing
`/?id=ID&r=-1` |GET| cancel given stream | nothing

The format returned in the subscribe calls is (EBNF):

```
Stream ::= Id Item* ( Error | Complete )?
Item ::= Length Byte*
Error ::= NegativeLength StackTrace 
StackTrace ::= Byte+
Complete ::= NegativeMinLength
```

### Stream
<img src="src/docs/Stream.png?raw=true"/><br/>

### Item
<img src="src/docs/Item.png?raw=true"/><br/>

### Complete
<img src="src/docs/Complete.png?raw=true"/><br/>

### Error
<img src="src/docs/Error.png?raw=true"/><br/>


The core of the library is support for publishing a `Flowable<ByteBuffer>` over HTTP(S). Serialization is a little optional extra that occurs at both ends.

### Nested Flowables
I have a design in mind for publishing nested Flowables over HTTP as well (representing the beginning of a nested Flowable with a special length value). I don't currently have a use case but if you do raise an issue and we'll implement it.

## Throughput
Peak throughput with embedded jetty server and client on same host, non-SSL, is about 1.3GB/s for 64K byte array items.

Throughput drops considerably for smaller byte arrays (because of overhead per array and frequent flushes):

| ByteBuffer size | Localhost Throughput (MB/s) |
| ---------------: | -----------------: |
| 2 | 0.27 |
| 4 | 0.53 |
| 8 | 1.2 |
| 128 | 17 |
| 512 | 68 |
| 2K | 245 |
| 8K | 744 |
| 32K | 1156 |
| 64K | 1300 |
| 128K | 1340 |

### Request patterns and flushing
Batching requests to balance backpressure and throughput is best tuned with benchmarking. Another aspect you can control is the flushing behaviour of the server. As items are received by the server flowable for publishing across the network to the client flowable each item is by default flushed to the `ServletOutputStream` so that the client gets it immediately instead of waiting for a buffer of bytes to be filled and then sent across. The flushing behaviour can be tuned in the servlet using the `Response` builder methods `autoFlush`, `flushAfterItems` and `flushAfterBytes`. You can specify both items count and byte count threshold at the same time. Here's an example:

```java
@WebServlet
public final class ServletAsync extends FlowableHttpServlet {

    @Override
    public Response respond(HttpServletRequest req) {
      return Response 
        .publisher(flowable)
        .flushAfterItems(10)
        .flushAfterBytes(8192)
        .build();
    }
}

```

Note that `autoFlush` doesn't do any flushing after `onNext` emissions but relies on the default buffering and flushing behaviour of the servlet. One exception for all the `flush` options that exists to prevent stream stalls under backpressure is that whenever the count of emissions on the server meets the current requested amount a flush is called.

### Server-specific optimizations

When a `ByteBuffer` on the server-side is written to the `ServletOutputStream` there are server-specific optimizations that can be made. For instance if the `ByteBuffer` from a memory mapped file and the server is Jetty 9 then the bytes don't need to be copied into the JVM process but can be transferred directly to the network channel by the operating system. Here's an example servlet using such an optimization (using the `writerFactory` builder method in `Response`):

```java
public class OptimizedJettyWriterServlet extends FlowableHttpServlet {

    @Override
    public Response respond(HttpServletRequest req) {
      return Response //
        .publisher(flowable) //
        .writerFactory(OptimizedJettyWriterFactory.INSTANCE) //
        .build();
    }
}
```
See [OptimizedJettyWriterFactory.java](src/test/java/org/davidmoten/rx2/http/OptimizedJettyWriterFactory.java) where you'll notice that the `ServletOutputStream` is cast to a `HttpOutput` which supports writing of `ByteBuffer`s directly.


