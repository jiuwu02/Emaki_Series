package emaki.jiuwu.craft.corelib.async;

/**
 * Legacy task handle kept only for third-party source compatibility.
 *
 * <p>This type has <strong>no remaining users in this repository</strong>: the only reference is
 * {@link FoliaSchedulerAdapter}, which is itself deprecated. Everything in-tree uses
 * {@link emaki.jiuwu.craft.corelib.execution.TaskHandle} instead (same simple name, different
 * package — do not confuse the two).
 *
 * <p>Scheduled for removal in the next major version. It is retained for one full minor cycle
 * because the package was published as part of CoreLib's public surface and an external plugin
 * could still compile against it. Before removal, re-run a source and binary usage check.
 *
 * @deprecated use {@link emaki.jiuwu.craft.corelib.execution.TaskHandle}, obtained from
 *         {@link emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher}, which carries the
 *         owner-thread guarantees this interface never expressed
 */
@Deprecated(forRemoval = true)
public interface TaskHandle {

    /** Cancels the task. */
    void cancel();

    /** @return whether the task has been cancelled */
    boolean isCancelled();
}
