package emaki.jiuwu.craft.corelib.monitor;

import java.util.Map;

public record PerformanceSnapshot(Map<String, PerformanceOperationSnapshot> operations,
                                  long usedMemoryBytes,
                                  long maxMemoryBytes,
                                  long capturedAt) {

    public double memoryUsageRatio() {
        return maxMemoryBytes <= 0L ? 0D : usedMemoryBytes / (double) maxMemoryBytes;
    }
}
