# rxjava2-http
Transmit RxJava2 Flowable over http (with backpressure of course)

Status: *pre-alpha* (in development)

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

## Design
WebSockets is a natural for this but can be blocked by corporate firewalls so this library starts with support for HTTP 1.0. 

Need API support for these actions:

* subscribe
* request
* cancel

This support is provided via these URL paths

Path | Action | Returns
--- | --- | ---
`/`   | subscribe with no request | Stream ID then binary stream
`/?r=REQUEST` | subscribe with initial request | Stream ID then binary stream
`/?id=ID&r=REQUEST` | request more from given stream | nothing
`/?id=ID&r=-1` | cancel given stream | nothing

The format returned in the subscribe calls is (EBNF):

```
Stream = Id {Item} [ Error | Complete ];
Id = SignedLong; // 8 bytes
Item = Length {Byte};
Length = SignedInteger; // 4 bytes
Error = NegativeLength {Byte};
NegativeLength = SignedInteger; // Not Integer.MIN_VALUE
Complete = Integer.MIN_VALUE // 4 bytes, SignedInteger
```
