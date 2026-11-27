package emaki.jiuwu.craft.corelib.api.async;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Shared bounded waiting on completion futures.
 *
 * <p>The deadline is expressed as a {@link System#nanoTime()} instant so that callers can
 * share one budget across several futures. Interruption restores the interrupt flag and
 * reports a timeout, exactly like a deadline expiry.
 */
public final class AsyncWaits {

    private AsyncWaits() {
    }

    /**
     * Blocks the calling thread until the future completes or the deadline passes.
     *
     * @param future future to wait for; must not be {@code null}
     * @param deadlineNanos absolute {@link System#nanoTime()} instant at which waiting stops
     * @return {@code true} when the future is done, {@code false} on deadline expiry or interruption
     * @throws NullPointerException when {@code future} is {@code null}
     */
    public static boolean awaitUntil(CompletableFuture<?> future, long deadlineNanos) {
        Objects.requireNonNull(future, "future");
        if (future.isDone()) {
            return true;
        }
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            return false;
        }
        CountDownLatch latch = new CountDownLatch(1);
        future.whenComplete((ignored, throwable) -> latch.countDown());
        try {
            return future.isDone() || latch.await(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
