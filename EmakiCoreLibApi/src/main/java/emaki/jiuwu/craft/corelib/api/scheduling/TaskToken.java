package emaki.jiuwu.craft.corelib.api.scheduling;

import org.jetbrains.annotations.ApiStatus;

/**
 * Cancellable handle for a task submitted through {@link EmakiScheduling}.
 *
 * <p>Store the token if the task must be stopped before it completes, typically in your plugin's
 * {@code onDisable}. Cancelling an already finished or already cancelled task is a no-op.
 */
@ApiStatus.NonExtendable
public interface TaskToken {

    /** Requests cancellation of the task. Safe to call more than once. */
    void cancel();

    /** {@return whether cancellation has been requested for this task} */
    boolean cancelled();

    /** Token returned when scheduling was not possible because EmakiCoreLib is unavailable. */
    TaskToken UNAVAILABLE = new TaskToken() {

        @Override
        public void cancel() {
            // Nothing was scheduled.
        }

        @Override
        public boolean cancelled() {
            return true;
        }
    };
}
