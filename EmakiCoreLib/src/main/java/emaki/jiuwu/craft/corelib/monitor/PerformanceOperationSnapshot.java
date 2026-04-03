package emaki.jiuwu.craft.corelib.monitor;

public record PerformanceOperationSnapshot(String operation,
                                           long count,
                                           long successCount,
                                           long failureCount,
                                           long warningCount,
                                           long totalDurationNanos,
                                           long averageDurationNanos,
                                           long maxDurationNanos,
                                           long lastDurationNanos,
                                           long lastUpdatedAt) {

    public double successRate() {
        return count <= 0L ? 1D : successCount / (double) count;
    }
}
