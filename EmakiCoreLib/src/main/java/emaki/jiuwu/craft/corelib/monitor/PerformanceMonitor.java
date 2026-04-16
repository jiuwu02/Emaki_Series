package emaki.jiuwu.craft.corelib.monitor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class PerformanceMonitor {

    private static final long DEFAULT_WARNING_THRESHOLD_NANOS = 50_000_000L;

    private final Map<String, MetricAccumulator> metrics = new ConcurrentHashMap<>();
    private final long warningThresholdNanos;

    public PerformanceMonitor() {
        this(DEFAULT_WARNING_THRESHOLD_NANOS);
    }

    public PerformanceMonitor(long warningThresholdNanos) {
        this.warningThresholdNanos = warningThresholdNanos <= 0L ? DEFAULT_WARNING_THRESHOLD_NANOS : warningThresholdNanos;
    }

    public <T> T measure(String operation, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier");
        long startedAt = System.nanoTime();
        boolean success = false;
        try {
            T value = supplier.get();
            success = true;
            return value;
        } finally {
            record(operation, System.nanoTime() - startedAt, success);
        }
    }

    public void measure(String operation, Runnable runnable) {
        measure(operation, () -> {
            runnable.run();
            return null;
        });
    }

    public void record(String operation, long durationNanos, boolean success) {
        MetricAccumulator accumulator = metrics.computeIfAbsent(normalizeOperation(operation), ignored -> new MetricAccumulator());
        accumulator.record(durationNanos, success, warningThresholdNanos);
    }

    public void clear() {
        metrics.clear();
    }

    public PerformanceSnapshot snapshot() {
        long usedMemoryBytes = Math.max(0L, Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        long maxMemoryBytes = Math.max(0L, Runtime.getRuntime().maxMemory());
        List<PerformanceOperationSnapshot> operations = new ArrayList<>(metrics.size());
        for (Map.Entry<String, MetricAccumulator> entry : metrics.entrySet()) {
            operations.add(entry.getValue().snapshot(entry.getKey()));
        }
        operations.sort(Comparator.comparingLong(PerformanceOperationSnapshot::totalDurationNanos).reversed()
                .thenComparing(PerformanceOperationSnapshot::operation));
        Map<String, PerformanceOperationSnapshot> orderedOperations = new LinkedHashMap<>(operations.size());
        for (PerformanceOperationSnapshot operation : operations) {
            orderedOperations.put(operation.operation(), operation);
        }
        return new PerformanceSnapshot(
                Map.copyOf(orderedOperations),
                usedMemoryBytes,
                maxMemoryBytes,
                System.currentTimeMillis()
        );
    }

    private String normalizeOperation(String operation) {
        return operation == null || operation.isBlank() ? "unknown" : operation.trim().toLowerCase();
    }

    private static final class MetricAccumulator {

        private final AtomicLong count = new AtomicLong();
        private final AtomicLong successCount = new AtomicLong();
        private final AtomicLong failureCount = new AtomicLong();
        private final AtomicLong warningCount = new AtomicLong();
        private final AtomicLong totalDurationNanos = new AtomicLong();
        private final AtomicLong maxDurationNanos = new AtomicLong();
        private final AtomicLong lastDurationNanos = new AtomicLong();
        private final AtomicLong lastUpdatedAt = new AtomicLong();

        private void record(long durationNanos, boolean success, long warningThresholdNanos) {
            count.incrementAndGet();
            if (success) {
                successCount.incrementAndGet();
            } else {
                failureCount.incrementAndGet();
            }
            if (durationNanos >= warningThresholdNanos) {
                warningCount.incrementAndGet();
            }
            totalDurationNanos.addAndGet(Math.max(0L, durationNanos));
            lastDurationNanos.set(Math.max(0L, durationNanos));
            lastUpdatedAt.set(System.currentTimeMillis());
            maxDurationNanos.accumulateAndGet(Math.max(0L, durationNanos), Math::max);
        }

        private PerformanceOperationSnapshot snapshot(String operation) {
            long totalCount = count.get();
            long totalDuration = totalDurationNanos.get();
            long average = totalCount <= 0L ? 0L : totalDuration / totalCount;
            return new PerformanceOperationSnapshot(
                    operation,
                    totalCount,
                    successCount.get(),
                    failureCount.get(),
                    warningCount.get(),
                    totalDuration,
                    average,
                    maxDurationNanos.get(),
                    lastDurationNanos.get(),
                    lastUpdatedAt.get()
            );
        }
    }
}
