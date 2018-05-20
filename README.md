# rxjava2-http
Transmit RxJava2 Flowable over http (with backpressure of course)

Status: *pre-alpha* (in development)

## Design

Interested in vanilla HTTP support for starters. WebSockets is a natural for this but can be blocked by corporate firewalls (mine does).

Need API support for these actions:

* subscribe
* request
* cancel

This support is provided via these URL paths

Path | Action | Returns
--- | --- | ---
/   | subscribe with no request | Stream ID then binary stream
/?r=REQUEST | subscribe with initial request | Stream ID then binary stream
/?id=ID&r=REQUEST | request more from given stream | nothing
/?id=ID&r=-1 | cancel given stream | nothing

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
