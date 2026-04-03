package emaki.jiuwu.craft.corelib.async;

public record AsyncTaskSnapshot(long submitted,
                                long completed,
                                long failed,
                                long timedOut,
                                int activeTasks,
                                int queuedTasks) {
}
