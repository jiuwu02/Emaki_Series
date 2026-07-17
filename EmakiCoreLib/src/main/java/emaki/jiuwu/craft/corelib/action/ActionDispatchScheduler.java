package emaki.jiuwu.craft.corelib.action;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.async.TaskHandle;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

final class ActionDispatchScheduler {

    private final Plugin plugin;
    private final AsyncTaskScheduler asyncTaskScheduler;
    private final PerformanceMonitor performanceMonitor;

    ActionDispatchScheduler(Plugin plugin) {
        this(plugin, null, null);
    }

    ActionDispatchScheduler(Plugin plugin,
            AsyncTaskScheduler asyncTaskScheduler,
            PerformanceMonitor performanceMonitor) {
        this.plugin = plugin;
        this.asyncTaskScheduler = asyncTaskScheduler;
        this.performanceMonitor = performanceMonitor;
    }

    CompletableFuture<ActionResult> dispatch(long delayTicks,
            String taskName,
            ActionExecutionMode mode,
            long timeoutMillis,
            Supplier<ActionResult> task) {
        ActionExecutionMode executionMode = mode == null ? ActionExecutionMode.SYNC : mode;
        if (executionMode == ActionExecutionMode.ASYNC_IO && asyncTaskScheduler != null) {
            return dispatchLegacyAsync(delayTicks, taskName, timeoutMillis, task);
        }
        ActionExecutionTarget target = executionMode == ActionExecutionMode.ASYNC_IO
                ? ActionExecutionTarget.async()
                : ActionExecutionTarget.global();
        return dispatch(plugin, target, delayTicks, taskName,
                () -> CompletableFuture.completedFuture(execute(task)))
                .exceptionally(this::failure);
    }

    <T> CompletableFuture<T> dispatch(Plugin owner,
            ActionExecutionTarget target,
            long delayTicks,
            String taskName,
            Supplier<? extends CompletionStage<T>> task) {
        Plugin effectiveOwner = owner == null ? plugin : owner;
        if (effectiveOwner == null || !effectiveOwner.isEnabled()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Source plugin is disabled."));
        }
        if (target == null || !target.valid()) {
            String message = target == null || target.failure() == null
                    ? "Action execution target is invalid."
                    : target.failure().errorMessage();
            return CompletableFuture.failedFuture(new IllegalStateException(message));
        }
        CompletableFuture<T> future = new CompletableFuture<>();
        long safeDelay = Math.max(0L, delayTicks);
        Runnable invocation = () -> invoke(taskName, task, future);
        try {
            TaskHandle handle = schedule(effectiveOwner, target, invocation, safeDelay);
            if (handle == null && !future.isDone()) {
                future.completeExceptionally(new IllegalStateException(
                        "Action scheduler rejected execution for plugin " + effectiveOwner.getName() + "."));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private CompletableFuture<ActionResult> dispatchLegacyAsync(long delayTicks,
            String taskName,
            long timeoutMillis,
            Supplier<ActionResult> task) {
        if (plugin == null || !plugin.isEnabled()) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionErrorType.INVALID_STATE, "Source plugin is disabled."));
        }
        long safeDelay = Math.max(0L, delayTicks);
        if (safeDelay > 0L) {
            CompletableFuture<ActionResult> future = new CompletableFuture<>();
            TaskHandle handle = FoliaSchedulerAdapter.runAsyncLater(plugin,
                    () -> dispatchLegacyAsync(0L, taskName, timeoutMillis, task)
                            .whenComplete((result, throwable) -> completeLegacy(future, result, throwable)),
                    Math.multiplyExact(safeDelay, 50L),
                    TimeUnit.MILLISECONDS);
            if (handle == null) {
                future.complete(ActionResult.failure(ActionErrorType.INVALID_STATE,
                        "Source plugin is disabled."));
            }
            return future;
        }
        return asyncTaskScheduler.supplyAsync(
                "action:" + safeTaskName(taskName),
                AsyncTaskScheduler.TaskPriority.LOW,
                timeoutMillis,
                () -> measure("action-dispatch:" + safeTaskName(taskName), task));
    }

    private TaskHandle schedule(Plugin owner,
            ActionExecutionTarget target,
            Runnable task,
            long delayTicks) {
        ExecutionDomain domain = target.domain();
        return switch (domain) {
            case SERVER_GLOBAL -> delayTicks > 0L
                    ? FoliaSchedulerAdapter.runTaskLater(owner, task, delayTicks)
                    : FoliaSchedulerAdapter.runTask(owner, task);
            case ENTITY -> scheduleEntity(owner, target.entity(), task, delayTicks);
            case LOCATION_REGION -> scheduleLocation(owner, target.location(), task, delayTicks);
            case ASYNC_COMPUTE -> delayTicks > 0L
                    ? FoliaSchedulerAdapter.runAsyncLater(owner, task,
                            Math.multiplyExact(delayTicks, 50L), TimeUnit.MILLISECONDS)
                    : FoliaSchedulerAdapter.runAsync(owner, task);
            case PHYSICAL_FILE -> null;
        };
    }

    private TaskHandle scheduleEntity(Plugin owner, Entity entity, Runnable task, long delayTicks) {
        if (entity == null) {
            return null;
        }
        return delayTicks > 0L
                ? FoliaSchedulerAdapter.runEntityTaskLater(owner, entity, task, delayTicks)
                : FoliaSchedulerAdapter.runEntityTask(owner, entity, task);
    }

    private TaskHandle scheduleLocation(Plugin owner, Location location, Runnable task, long delayTicks) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return delayTicks > 0L
                ? FoliaSchedulerAdapter.runAtLocationLater(owner, location, task, delayTicks)
                : FoliaSchedulerAdapter.runAtLocation(owner, location, task);
    }

    private <T> void invoke(String taskName,
            Supplier<? extends CompletionStage<T>> task,
            CompletableFuture<T> future) {
        if (future.isDone()) {
            return;
        }
        try {
            CompletionStage<T> stage = performanceMonitor == null
                    ? task.get()
                    : performanceMonitor.measure("action-dispatch:" + safeTaskName(taskName), task::get);
            if (stage == null) {
                future.completeExceptionally(new IllegalStateException(
                        "Action returned a null completion stage."));
                return;
            }
            stage.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    future.completeExceptionally(throwable);
                } else {
                    future.complete(result);
                }
            });
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private ActionResult measure(String metricKey, Supplier<ActionResult> task) {
        return performanceMonitor == null ? execute(task) : performanceMonitor.measure(metricKey, () -> execute(task));
    }

    private ActionResult execute(Supplier<ActionResult> task) {
        try {
            ActionResult result = task.get();
            return result == null ? ActionResult.ok() : result;
        } catch (Exception exception) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, exception.getMessage());
        }
    }

    private void completeLegacy(CompletableFuture<ActionResult> future,
            ActionResult result,
            Throwable throwable) {
        if (throwable != null) {
            future.complete(failure(throwable));
        } else {
            future.complete(result == null ? ActionResult.ok() : result);
        }
    }

    private ActionResult failure(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null && cause.getCause() != null
                && (cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)) {
            cause = cause.getCause();
        }
        String message = cause == null ? "Unknown action execution failure." : cause.getMessage();
        return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, message);
    }

    private String safeTaskName(String taskName) {
        return taskName == null || taskName.isBlank() ? "unknown" : taskName.trim();
    }
}
