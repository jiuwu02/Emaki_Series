package emaki.jiuwu.craft.corelib.api.action;

/**
 * Declares where a CoreLib action may be executed.
 */
public enum CoreActionExecutionMode {
    /** Execute on the server main thread. */
    SYNC,
    /** Execute on CoreLib's async IO scheduler; implementations must not touch Bukkit main-thread-only APIs. */
    ASYNC_IO
}
