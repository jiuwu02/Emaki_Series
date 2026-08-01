package emaki.jiuwu.craft.corelib.api.action;

import org.jetbrains.annotations.NotNull;

/**
 * Cooperative cancellation signal supplied to a running pipeline.
 *
 * <p>The interpreter cancels this token when a stage times out or its owner is disabled. Long-running
 * stages should periodically read it and stop without touching Bukkit state after cancellation.</p>
 */
@FunctionalInterface
public interface CoreCancellationToken {

    /** {@return whether the current invocation has been cancelled} */
    boolean cancelled();

    /** {@return a token that is never cancelled} */
    static @NotNull CoreCancellationToken none() {
        return () -> false;
    }
}
