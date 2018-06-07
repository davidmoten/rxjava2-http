package org.davidmoten.rx2.io.internal;

import java.util.concurrent.CountDownLatch;

import org.junit.Test;
import org.mockito.Mockito;

public class ServletHandlerTest {
    
    @Test
    public void testWait() {
        CountDownLatch latch = new CountDownLatch(1);
        latch.countDown();
        ServletHandler.waitFor(latch);
    }
    
    @Test
    public void testWaitInterruption() throws InterruptedException {
        CountDownLatch latch = Mockito.mock(CountDownLatch.class);
        Mockito.doThrow(new InterruptedException()).when(latch).await();
        latch.countDown();
        ServletHandler.waitFor(latch);
    }

}
