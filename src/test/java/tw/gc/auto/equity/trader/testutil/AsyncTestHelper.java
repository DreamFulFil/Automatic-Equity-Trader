package tw.gc.auto.equity.trader.testutil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Test utility for handling asynchronous operations in unit and integration tests.
 * Provides helpers for waiting on conditions, latches, and timeouts.
 */
public class AsyncTestHelper {

    /**
     * Wait for a condition to become true within a timeout period.
     * Polls the condition every 50ms.
     *
     * @param timeoutMillis maximum time to wait in milliseconds
     * @param condition the condition to check
     * @return true if condition became true within timeout, false otherwise
     */
    public static boolean waitForAsync(long timeoutMillis, BooleanSupplier condition) {
        long start = System.currentTimeMillis();
        long elapsed;
        
        while ((elapsed = System.currentTimeMillis() - start) < timeoutMillis) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }

    /**
     * Create a CountDownLatch with specified count.
     *
     * @param count the number of times countDown must be invoked
     * @return a new CountDownLatch
     */
    public static CountDownLatch createLatch(int count) {
        return new CountDownLatch(count);
    }

    /**
     * Wait for a latch to count down within the specified timeout.
     * Throws AssertionError if timeout is exceeded.
     *
     * @param latch the latch to await
     * @param timeoutSec timeout in seconds
     * @throws AssertionError if the latch does not count down in time
     */
    public static void awaitLatch(CountDownLatch latch, long timeoutSec) {
        try {
            boolean success = latch.await(timeoutSec, TimeUnit.SECONDS);
            if (!success) {
                throw new AssertionError("Latch timeout: expected count to reach 0 within " 
                    + timeoutSec + " seconds, but was " + latch.getCount());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Latch wait interrupted", e);
        }
    }

    /**
     * Wait for a latch to count down within the specified timeout.
     * Returns false if timeout is exceeded instead of throwing.
     *
     * @param latch the latch to await
     * @param timeoutSec timeout in seconds
     * @return true if latch counted down, false if timeout
     */
    public static boolean awaitLatchQuietly(CountDownLatch latch, long timeoutSec) {
        try {
            return latch.await(timeoutSec, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Sleep for the specified duration, handling interruption gracefully.
     *
     * @param millis milliseconds to sleep
     */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
