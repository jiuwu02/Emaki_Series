package emaki.jiuwu.craft.corelib.script;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.ActionBatchResult;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.async.TaskHandle;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Collects side-effect intents while guest JavaScript runs on an isolated worker.
 * The guest thread can only append immutable intent data; Bukkit/API work is
 * dispatched after the polyglot context has closed.
 */
public final class ScriptDeferredOperationQueue {

    private static final ThreadLocal<ModuleCapture> MODULE_CAPTURE = new ThreadLocal<>();

    private final Plugin schedulerOwner;
    private final ActionExecutor actionExecutor;
    private final ActionContext actionContext;
    private final Queue<DeferredOperation> operations = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean sealed = new AtomicBoolean();

    public ScriptDeferredOperationQueue(Plugin schedulerOwner,
            ActionExecutor actionExecutor,
            ActionContext actionContext) {
        this.schedulerOwner = schedulerOwner;
        this.actionExecutor = actionExecutor;
        this.actionContext = actionContext;
    }

    public static <T> T withModuleCapture(ScriptDeferredOperationQueue deferredOperations,
            ActionContext liveActionContext,
            Supplier<T> capture) {
        Objects.requireNonNull(capture, "capture");
        ModuleCapture previous = MODULE_CAPTURE.get();
        MODULE_CAPTURE.set(new ModuleCapture(deferredOperations, liveActionContext));
        try {
            return capture.get();
        } finally {
            if (previous == null) {
                MODULE_CAPTURE.remove();
            } else {
                MODULE_CAPTURE.set(previous);
            }
        }
    }

    public static ScriptDeferredOperationQueue currentModuleQueue() {
        ModuleCapture capture = MODULE_CAPTURE.get();
        return capture == null ? null : capture.deferredOperations();
    }

    public static ActionContext currentModuleActionContext() {
        ModuleCapture capture = MODULE_CAPTURE.get();
        return capture == null ? null : capture.liveActionContext();
    }

    public boolean enqueueAction(String actionId, Map<String, String> arguments) {
        return enqueueAction(actionId, arguments, null, 0);
    }

    public boolean enqueueAction(String actionId,
            Map<String, String> arguments,
            String depthKey,
            int maxDepth) {
        if (actionExecutor == null || actionContext == null || Texts.isBlank(actionId)) {
            return false;
        }
        Map<String, String> safeArguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        return enqueue("action:" + Texts.normalizeId(actionId), () -> dispatchOnContextDomain(() ->
                withActionDepth(depthKey, maxDepth, () -> actionExecutor.execute(actionContext, actionId, safeArguments)
                        .thenApply(this::fromActionResult))));
    }

    public boolean enqueueActionLine(String actionLine) {
        return enqueueActionLine(actionLine, null, 0);
    }

    public boolean enqueueActionLine(String actionLine, String depthKey, int maxDepth) {
        if (actionExecutor == null || actionContext == null || Texts.isBlank(actionLine)) {
            return false;
        }
        String safeLine = Texts.toStringSafe(actionLine);
        return enqueue("action-line", () -> dispatchOnContextDomain(() ->
                withActionDepth(depthKey, maxDepth, () ->
                        actionExecutor.executeAll(actionContext, java.util.List.of(safeLine), true)
                                .thenApply(this::fromActionBatchResult))));
    }

    public boolean enqueueGlobal(String description, Runnable operation) {
        return enqueueGlobal(schedulerOwner, description, operation);
    }

    public boolean enqueueGlobal(Plugin operationOwner, String description, Runnable operation) {
        if (operationOwner == null || operation == null) {
            return false;
        }
        return enqueue(description, () -> scheduleGlobal(operationOwner, operation));
    }

    public boolean enqueueGlobalResult(String description, Supplier<OperationResult> operation) {
        return enqueueGlobalResult(schedulerOwner, description, operation);
    }

    public boolean enqueueGlobalResult(Plugin operationOwner,
            String description,
            Supplier<OperationResult> operation) {
        if (operationOwner == null || operation == null) {
            return false;
        }
        return enqueue(description, () -> scheduleGlobalResult(operationOwner, operation));
    }

    public boolean enqueueContext(String description, Runnable operation) {
        if (operation == null) {
            return false;
        }
        return enqueue(description, () -> dispatchOnContextDomain(() -> {
            operation.run();
            return CompletableFuture.completedFuture(OperationResult.ok());
        }));
    }

    public boolean enqueueEntity(String description, Entity entity, Consumer<Entity> operation) {
        if (entity == null || operation == null) {
            return false;
        }
        return enqueue(description, () -> scheduleEntity(entity, operation));
    }

    public boolean enqueueEntityAsync(String description,
            Entity entity,
            Function<Entity, ? extends CompletionStage<OperationResult>> operation) {
        if (entity == null || operation == null) {
            return false;
        }
        return enqueue(description, () -> scheduleEntityAsync(entity, operation));
    }

    public boolean enqueueLocation(String description, Location location, Runnable operation) {
        if (location == null || operation == null) {
            return false;
        }
        return enqueue(description, () -> scheduleLocation(location, operation));
    }

    public boolean enqueueLocation(String description,
            Supplier<Location> locationSupplier,
            Consumer<Location> operation) {
        if (locationSupplier == null || operation == null) {
            return false;
        }
        return enqueue(description, () -> {
            Location location = locationSupplier.get();
            if (location == null) {
                return CompletableFuture.completedFuture(OperationResult.failure(
                        "Deferred location operation has no target location."));
            }
            return scheduleLocation(location, () -> operation.accept(location));
        });
    }

    public boolean enqueue(String description, Supplier<? extends CompletionStage<OperationResult>> operation) {
        if (operation == null || sealed.get()) {
            return false;
        }
        operations.add(new DeferredOperation(Texts.toStringSafe(description), operation));
        if (sealed.get() && operations.removeIf(queued -> queued.operation() == operation)) {
            return false;
        }
        return true;
    }

    public boolean isEmpty() {
        return operations.isEmpty();
    }

    public void discard() {
        sealed.set(true);
        operations.clear();
    }

    public CompletableFuture<ScriptExecutionResult> drain(ScriptExecutionResult scriptResult) {
        sealed.set(true);
        if (scriptResult == null) {
            operations.clear();
            return CompletableFuture.completedFuture(ScriptExecutionResult.failure(
                    "Script execution ended without a result."));
        }
        if (!scriptResult.success() || scriptResult.skipped()) {
            operations.clear();
            return CompletableFuture.completedFuture(scriptResult);
        }
        CompletableFuture<ScriptExecutionResult> future = new CompletableFuture<>();
        executeNext(scriptResult, future);
        return future;
    }

    private void executeNext(ScriptExecutionResult scriptResult,
            CompletableFuture<ScriptExecutionResult> future) {
        DeferredOperation deferred = operations.poll();
        if (deferred == null) {
            future.complete(scriptResult);
            return;
        }
        CompletionStage<OperationResult> stage;
        try {
            stage = deferred.operation().get();
        } catch (Throwable throwable) {
            future.complete(ScriptExecutionResult.failure(
                    operationFailureMessage(deferred.description(), throwable), unwrap(throwable)));
            operations.clear();
            return;
        }
        if (stage == null) {
            future.complete(ScriptExecutionResult.failure(
                    "Deferred script operation returned no completion stage: " + deferred.description()));
            operations.clear();
            return;
        }
        stage.whenComplete((result, throwable) -> {
            Throwable failure = unwrap(throwable);
            if (failure != null) {
                operations.clear();
                future.complete(ScriptExecutionResult.failure(
                        operationFailureMessage(deferred.description(), failure), failure));
                return;
            }
            OperationResult completed = result == null
                    ? OperationResult.failure("Deferred operation returned no result.")
                    : result;
            if (!completed.success()) {
                operations.clear();
                future.complete(ScriptExecutionResult.failure(Texts.isBlank(completed.message())
                        ? "Deferred script operation failed: " + deferred.description()
                        : completed.message()));
                return;
            }
            executeNext(scriptResult, future);
        });
    }

    private CompletionStage<OperationResult> withActionDepth(String depthKey,
            int maxDepth,
            Supplier<? extends CompletionStage<OperationResult>> operation) {
        if (Texts.isBlank(depthKey) || maxDepth <= 0) {
            return operation.get();
        }
        Object raw = actionContext.sharedValue(depthKey);
        int depth = raw instanceof Number number ? Math.max(0, number.intValue()) : 0;
        if (depth >= maxDepth) {
            return CompletableFuture.completedFuture(OperationResult.failure(
                    "Maximum nested script Action depth exceeded."));
        }
        actionContext.sharedState().put(depthKey, depth + 1);
        CompletionStage<OperationResult> stage;
        try {
            stage = operation.get();
        } catch (Throwable throwable) {
            restoreActionDepth(depthKey, depth);
            throw throwable;
        }
        if (stage == null) {
            restoreActionDepth(depthKey, depth);
            return CompletableFuture.completedFuture(OperationResult.failure(
                    "Deferred Action returned no completion stage."));
        }
        return stage.whenComplete((_, _) -> restoreActionDepth(depthKey, depth));
    }

    private void restoreActionDepth(String depthKey, int depth) {
        if (depth <= 0) {
            actionContext.sharedState().remove(depthKey);
        } else {
            actionContext.sharedState().put(depthKey, depth);
        }
    }

    private CompletableFuture<OperationResult> dispatchOnContextDomain(
            Supplier<? extends CompletionStage<OperationResult>> operation) {
        if (schedulerOwner == null || !schedulerOwner.isEnabled()) {
            return CompletableFuture.completedFuture(OperationResult.failure(
                    "Script operation scheduler owner is unavailable."));
        }
        CompletableFuture<OperationResult> future = new CompletableFuture<>();
        Runnable task = () -> flatten(operation, future);
        try {
            TaskHandle handle = actionContext != null && actionContext.player() != null
                    ? FoliaSchedulerAdapter.runEntityTask(schedulerOwner, actionContext.player(), task)
                    : FoliaSchedulerAdapter.runTask(schedulerOwner, task);
            if (handle == null) {
                future.complete(OperationResult.failure(
                        "Deferred context operation scheduling was rejected."));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private CompletableFuture<OperationResult> scheduleGlobal(Plugin operationOwner, Runnable operation) {
        CompletableFuture<OperationResult> future = new CompletableFuture<>();
        try {
            TaskHandle handle = FoliaSchedulerAdapter.runTask(
                    operationOwner, () -> runOperation(operation, future));
            if (handle == null) {
                future.complete(OperationResult.failure(
                        "Deferred global operation scheduling was rejected."));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private CompletableFuture<OperationResult> scheduleGlobalResult(Plugin operationOwner,
            Supplier<OperationResult> operation) {
        CompletableFuture<OperationResult> future = new CompletableFuture<>();
        try {
            TaskHandle handle = FoliaSchedulerAdapter.runTask(operationOwner, () -> {
                try {
                    OperationResult result = operation.get();
                    future.complete(result == null
                            ? OperationResult.failure("Deferred global operation returned no result.")
                            : result);
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
            if (handle == null) {
                future.complete(OperationResult.failure(
                        "Deferred global result operation scheduling was rejected."));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private CompletableFuture<OperationResult> scheduleEntity(Entity entity, Consumer<Entity> operation) {
        CompletableFuture<OperationResult> future = new CompletableFuture<>();
        try {
            TaskHandle handle = FoliaSchedulerAdapter.runEntityTask(schedulerOwner, entity, () -> {
                try {
                    operation.accept(entity);
                    future.complete(OperationResult.ok());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
            if (handle == null) {
                future.complete(OperationResult.failure(
                        "Deferred entity operation scheduling was rejected."));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private CompletableFuture<OperationResult> scheduleEntityAsync(Entity entity,
            Function<Entity, ? extends CompletionStage<OperationResult>> operation) {
        CompletableFuture<OperationResult> future = new CompletableFuture<>();
        try {
            TaskHandle handle = FoliaSchedulerAdapter.runEntityTask(schedulerOwner, entity, () -> {
                try {
                    CompletionStage<OperationResult> stage = operation.apply(entity);
                    if (stage == null) {
                        future.complete(OperationResult.failure("Deferred entity operation returned no stage."));
                    } else {
                        stage.whenComplete((result, throwable) -> {
                            if (throwable != null) {
                                future.completeExceptionally(unwrap(throwable));
                            } else {
                                future.complete(result == null
                                        ? OperationResult.failure("Deferred entity operation returned no result.")
                                        : result);
                            }
                        });
                    }
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
            if (handle == null) {
                future.complete(OperationResult.failure(
                        "Deferred asynchronous entity operation scheduling was rejected."));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private CompletableFuture<OperationResult> scheduleLocation(Location location, Runnable operation) {
        CompletableFuture<OperationResult> future = new CompletableFuture<>();
        try {
            TaskHandle handle = FoliaSchedulerAdapter.runAtLocation(
                    schedulerOwner, location, () -> runOperation(operation, future));
            if (handle == null) {
                future.complete(OperationResult.failure(
                        "Deferred location operation scheduling was rejected."));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private void runOperation(Runnable operation, CompletableFuture<OperationResult> future) {
        try {
            operation.run();
            future.complete(OperationResult.ok());
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private void flatten(Supplier<? extends CompletionStage<OperationResult>> operation,
            CompletableFuture<OperationResult> future) {
        try {
            CompletionStage<OperationResult> stage = operation.get();
            if (stage == null) {
                future.complete(OperationResult.failure("Deferred operation returned no completion stage."));
                return;
            }
            stage.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    future.completeExceptionally(unwrap(throwable));
                } else {
                    future.complete(result == null
                            ? OperationResult.failure("Deferred operation returned no result.")
                            : result);
                }
            });
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private OperationResult fromActionResult(ActionResult result) {
        if (result == null) {
            return OperationResult.failure("Deferred Action returned no result.");
        }
        return result.success()
                ? OperationResult.ok()
                : OperationResult.failure(Texts.toStringSafe(result.errorMessage()));
    }

    private OperationResult fromActionBatchResult(ActionBatchResult result) {
        if (result == null) {
            return OperationResult.failure("Deferred Action line returned no result.");
        }
        if (result.success()) {
            return OperationResult.ok();
        }
        String message = result.steps().stream()
                .map(step -> step.result())
                .filter(Objects::nonNull)
                .filter(step -> !step.success())
                .map(ActionResult::errorMessage)
                .filter(Texts::isNotBlank)
                .findFirst()
                .orElse("Deferred Action line failed.");
        return OperationResult.failure(message);
    }

    private String operationFailureMessage(String description, Throwable throwable) {
        String message = throwable == null ? "" : throwable.getMessage();
        return "Deferred script operation failed"
                + (Texts.isBlank(description) ? "" : " (" + description + ")")
                + (Texts.isBlank(message) ? "." : ": " + message);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public record OperationResult(boolean success, String message) {

        public static OperationResult ok() {
            return new OperationResult(true, "");
        }

        public static OperationResult failure(String message) {
            return new OperationResult(false, Texts.toStringSafe(message));
        }
    }

    private record DeferredOperation(String description,
            Supplier<? extends CompletionStage<OperationResult>> operation) {
    }

    private record ModuleCapture(ScriptDeferredOperationQueue deferredOperations,
            ActionContext liveActionContext) {
    }
}
