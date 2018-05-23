# rxjava2-http
<a href="https://travis-ci.org/davidmoten/rxjava2-http"><img src="https://travis-ci.org/davidmoten/rxjava2-http.svg"/></a><br/>
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.davidmoten/rxjava2-http/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.github.davidmoten/rxjava2-http)<br/>
[![codecov](https://codecov.io/gh/davidmoten/rxjava2-http/branch/master/graph/badge.svg)](https://codecov.io/gh/davidmoten/rxjava2-http)

Transmit RxJava2 Flowable over http (with non-blocking backpressure).

Status: *pre-alpha* (in development)

## Features
* Apply non-blocking backpressure to streams over networks
* use whatever serialization library you want (the core supports `Flowable<ByteBuffer>`)
* supports plain HTTP/HTTPS service (no firewall troubles with WebSockets)
* may support NIO later

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
The servlet below exposes the `Flowable.range(1, 1000)` stream across HTTP:

```java
@WebServlet(urlPatterns={"/stream"})
public class HandlerServlet extends FlowableHttpServlet {
      
    public HandlerServlet() {
        super(
          Flowable
            .range(1,1000)
            .map(DefaultSerializer.instance()::serialize)      
        );
    }
}
```
The default behaviour is to schedule requests on `Schedulers.io()` but this is configurable via another `FlowableHttpServlet` constructor.

### Create client
Assuming the servlet above is listening on `http://localhost:8080/stream`, this is how you access it over HTTP:

```java
Flowable<Integer> numbers = 
    Client
       .get("http://localhost:8080/")
       .deserialized();
```
More client options are available. Here is an example:

```java
Flowable<Integer> numbers = 
	Client
	  .get("http://localhost:8080/") //
	  .method(HttpMethod.GET) // request in batches of 100, default is 16
	  .serializer(serializer) // used for deserialization
	  .rebatchRequests(128); // necessary to enable backpressure over the network without blocking calls
```

### Serializers

`Serializer.javaIo()` can be used to serialize classes that implement `Serializable`. It is much slower than products like *Kryo* or indeed if you have the time, custom serialization.

### Good practices

Note that a quiet source Flowable over http(s) is indistinguishable from a chopped connection (by a firewall for instance). To avoid this:

* regularly cancel and reconnect to the stream

OR

* include a heartbeat emission in the Flowable which you filter out on the client side

OR

* put a `timeout` operator on the Flowable on the client side

## Design
WebSockets is a natural for this but can be blocked by corporate firewalls so this library starts with support for HTTP 1.0. 

We want API support for these actions:

* subscribe
* request
* cancel

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
Error ::= NegativeLength Byte*
Complete ::= NegativeMinLength
```

### Stream
<img src="https://raw.githubusercontent.com/davidmoten/rxjava2-http/master/src/doc/Stream.png"/><br/>

### Item
<img src="https://raw.githubusercontent.com/davidmoten/rxjava2-http/master/src/doc/Item.png"/><br/>

### Complete
<img src="https://raw.githubusercontent.com/davidmoten/rxjava2-http/master/src/doc/Complete.png"/><br/>

### Error
<img src="https://raw.githubusercontent.com/davidmoten/rxjava2-http/master/src/doc/Error.png"/><br/>


The core of the library is support for publishing a `Flowable<ByteBuffer>` over HTTP. Serialization is a little optional extra that occurs at both ends.

### Nested Flowables
I have a design in mind for publishing nested Flowables over HTTP as well (representing the beginning of a nested Flowable with a special length value). I don't currently have a use case but if you do raise an issue and we'll implement it.


