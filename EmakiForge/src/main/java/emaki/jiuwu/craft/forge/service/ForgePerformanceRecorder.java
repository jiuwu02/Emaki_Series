package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;

final class ForgePerformanceRecorder {

    private final PerformanceMonitor performanceMonitor;

    ForgePerformanceRecorder(PerformanceMonitor performanceMonitor) {
        this.performanceMonitor = performanceMonitor;
    }

    <T> T measure(String metricKey, SupplierWithException<T> supplier) {
        long startedAt = System.nanoTime();
        boolean success = false;
        try {
            T value = supplier.get();
            success = true;
            return value;
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            if (performanceMonitor != null) {
                performanceMonitor.record(metricKey, System.nanoTime() - startedAt, success);
            }
        }
    }

    @FunctionalInterface
    interface SupplierWithException<T> {

        T get() throws Exception;
    }
}
