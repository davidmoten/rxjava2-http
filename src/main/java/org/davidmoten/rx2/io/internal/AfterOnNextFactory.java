package org.davidmoten.rx2.io.internal;

public interface AfterOnNextFactory {

    AfterOnNext create();

    public static final AfterOnNextFactory DEFAULT = flushAfter(0, 0); 
    
    public static AfterOnNextFactory flushAfter(int numItems, int numBytes) {
        return new AfterOnNextFactory() {

            @Override
            public AfterOnNext create() {
                return new AfterOnNext() {

                    int countItems;
                    int countBytes;

                    @Override
                    public boolean flushRequested(int n) {
                        countItems++;
                        countBytes += n;
                        final boolean flush;
                        if (numItems > 0 && countItems == numItems) {
                            flush = true;
                        } else if (numBytes > 0 && countBytes >= numBytes) {
                            flush = true;
                        } else {
                            flush = false;
                        }
                        if (flush) {
                            countItems = 0;
                            countBytes = 0;
                        }
                        return flush;
                    }

                };
            }

        };
    }
}
