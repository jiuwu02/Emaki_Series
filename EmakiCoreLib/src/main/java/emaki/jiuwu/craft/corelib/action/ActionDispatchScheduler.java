package emaki.jiuwu.craft.corelib.action;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;

final class ActionDispatchScheduler {

    private final Plugin plugin;
    private final AsyncTaskScheduler asyncTaskScheduler;
    private final PerformanceMonitor performanceMonitor;

    ActionDispatchScheduler(Plugin plugin) {
        this.plugin = plugin;
        EmakiCoreLibPlugin coreLibPlugin = EmakiCoreLibPlugin.getInstance();
        this.asyncTaskScheduler = coreLibPlugin == null ? null : coreLibPlugin.asyncTaskScheduler();
        this.performanceMonitor = coreLibPlugin == null ? null : coreLibPlugin.performanceMonitor();
    }

    CompletableFuture<ActionResult> dispatch(long delayTicks,
            String taskName,
            ActionExecutionMode mode,
            long timeoutMillis,
            Supplier<ActionResult> task) {
        if (plugin == null || !plugin.isEnabled()) {
            return CompletableFuture.completedFuture(ActionResult.failure(ActionErrorType.INVALID_STATE, "Source plugin is disabled."));
        }
        long safeDelay = Math.max(0L, delayTicks);
        ActionExecutionMode executionMode = mode == null ? ActionExecutionMode.SYNC : mode;
        if (safeDelay > 0L) {
            CompletableFuture<ActionResult> future = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> dispatchNow(taskName, executionMode, timeoutMillis, task)
                    .whenComplete((result, throwable) -> completeFuture(future, result, throwable)), safeDelay);
            return future;
        }
        return dispatchNow(taskName, executionMode, timeoutMillis, task);
    }

    private CompletableFuture<ActionResult> dispatchNow(String taskName,
            ActionExecutionMode mode,
            long timeoutMillis,
            Supplier<ActionResult> task) {
        if (mode == ActionExecutionMode.ASYNC_IO && asyncTaskScheduler != null) {
            return asyncTaskScheduler.supplyAsync(
                    "action:" + safeTaskName(taskName),
                    AsyncTaskScheduler.TaskPriority.LOW,
                    timeoutMillis,
                    () -> measure("action-dispatch:" + safeTaskName(taskName), task)
            );
        }
        if (Bukkit.isPrimaryThread()) {
            return CompletableFuture.completedFuture(measure("action-dispatch:" + safeTaskName(taskName), task));
        }
        CompletableFuture<ActionResult> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> completeFuture(future, measure("action-dispatch:" + safeTaskName(taskName), task), null));
        return future;
    }

    private ActionResult measure(String metricKey, Supplier<ActionResult> task) {
        if (performanceMonitor == null) {
            return execute(task);
        }
        return performanceMonitor.measure(metricKey, () -> execute(task));
    }

    private ActionResult execute(Supplier<ActionResult> task) {
        try {
            ActionResult result = task.get();
            return result == null ? ActionResult.ok() : result;
        } catch (Exception exception) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, exception.getMessage());
        }
    }

    private void completeFuture(CompletableFuture<ActionResult> future, ActionResult result, Throwable throwable) {
        if (future == null) {
            return;
        }
        if (throwable != null) {
            future.complete(ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, throwable.getMessage()));
            return;
        }
        future.complete(result == null ? ActionResult.ok() : result);
    }

    private String safeTaskName(String taskName) {
        return taskName == null || taskName.isBlank() ? "unknown" : taskName.trim();
    }
}
