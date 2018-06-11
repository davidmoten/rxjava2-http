package org.davidmoten.rx2.io.internal;

public interface AfterOnNext {

    boolean shouldFlush(int numBytes);

}
