package emaki.jiuwu.craft.corelib.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.corelib.plugin.AbstractEmakiPlugin;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ActionExecutor {

    private static final String DEBUG_MODULE = "action";

    private final Plugin plugin;
    private final ActionRegistry registry;
    private final ActionLineParser lineParser;
    private final PlaceholderRegistry placeholderRegistry;
    private final ActionTemplateProcessor templateProcessor;
    private final ActionDispatchScheduler dispatchScheduler;
    private final ActionInvocationPlanner invocationPlanner;

    public ActionExecutor(@NotNull Plugin plugin,
            @NotNull ActionRegistry registry,
            @NotNull ActionLineParser lineParser,
            @NotNull PlaceholderRegistry placeholderRegistry,
            @NotNull ActionTemplateRegistry templateRegistry) {
        this(plugin, registry, lineParser, placeholderRegistry, templateRegistry, null, null);
    }

    public ActionExecutor(@NotNull Plugin plugin,
            @NotNull ActionRegistry registry,
            @NotNull ActionLineParser lineParser,
            @NotNull PlaceholderRegistry placeholderRegistry,
            @NotNull ActionTemplateRegistry templateRegistry,
            @Nullable AsyncTaskScheduler asyncTaskScheduler,
            @Nullable PerformanceMonitor performanceMonitor) {
        this.plugin = plugin;
        this.registry = registry;
        this.lineParser = lineParser;
        this.placeholderRegistry = placeholderRegistry;
        this.templateProcessor = new ActionTemplateProcessor(plugin, templateRegistry);
        this.dispatchScheduler = new ActionDispatchScheduler(plugin, asyncTaskScheduler, performanceMonitor);
        this.invocationPlanner = new ActionInvocationPlanner(placeholderRegistry, dispatchScheduler);
    }

    @NotNull
    public CompletableFuture<ActionResult> execute(@NotNull ActionContext context,
            @NotNull String actionId,
            @Nullable Map<String, String> arguments) {
        debug(context, "execute direct | phase=" + context.phase() + " | action=" + actionId
                + " | rawArgs=" + summarizeMap(arguments));
        PlaceholderRenderer.debugVariables(
                PlaceholderRenderer.contextVariables(context),
                resolveDebugLogger(context),
                context.player(),
                "action.direct." + actionId);
        RegisteredAction registration = registry.getRegistered(actionId);
        if (registration == null) {
            ActionResult result = missingActionResult(actionId);
            debug(context, "execute direct missing | action=" + actionId + " | error=" + result.errorMessage());
            return CompletableFuture.completedFuture(result);
        }
        return executeRegistered(context, registration, arguments, 0L, true)
                .whenComplete((result, throwable) -> debug(context,
                        "execute direct result | action=" + actionId
                                + " | success=" + (throwable == null && result != null && result.success())
                                + " | error=" + (throwable == null
                                ? (result == null ? "" : result.errorMessage())
                                : throwable.getMessage())));
    }

    @NotNull
    public CompletableFuture<ActionBatchResult> executeAll(@NotNull ActionContext context,
            @Nullable List<String> lines,
            boolean stopOnFailure) {
        List<String> safeLines = lines == null ? List.of() : lines;
        debug(context, "execute batch start | phase=" + context.phase()
                + " | lines=" + safeLines.size()
                + " | stopOnFailure=" + stopOnFailure);
        PlaceholderRenderer.debugVariables(
                PlaceholderRenderer.contextVariables(context),
                resolveDebugLogger(context),
                context.player(),
                "action." + context.phase());
        CompletableFuture<ActionBatchResult> future = new CompletableFuture<>();
        executeIndex(context, safeLines, stopOnFailure, 0, new ArrayList<>(), future);
        return future.whenComplete((batch, throwable) -> debug(context,
                "execute batch result | phase=" + context.phase()
                        + " | success=" + (throwable == null && batch != null && batch.success())
                        + " | steps=" + (batch == null ? 0 : batch.steps().size())
                        + " | error=" + (throwable == null ? "" : throwable.getMessage())));
    }

    private void executeIndex(ActionContext context,
            List<String> lines,
            boolean stopOnFailure,
            int index,
            List<ActionStepResult> steps,
            CompletableFuture<ActionBatchResult> future) {
        if (index >= lines.size()) {
            future.complete(new ActionBatchResult(true, List.copyOf(steps)));
            return;
        }
        ParsedActionLine parsed;
        String rawLine = lines.get(index);
        debug(context, "parse line | phase=" + context.phase()
                + " | line=" + (index + 1)
                + " | raw=" + summarize(rawLine));
        try {
            parsed = lineParser.parse(index + 1, rawLine);
        } catch (ActionSyntaxException exception) {
            debug(context, "parse failed | phase=" + context.phase()
                    + " | line=" + exception.lineNumber()
                    + " | error=" + exception.getMessage());
            steps.add(new ActionStepResult(
                    exception.lineNumber(),
                    exception.rawLine(),
                    "",
                    ActionResult.failure(ActionErrorType.SYNTAX_ERROR, exception.getMessage())));
            future.complete(new ActionBatchResult(false, List.copyOf(steps)));
            return;
        }
        if (parsed == null) {
            debug(context, "parse skipped | phase=" + context.phase() + " | line=" + (index + 1));
            executeIndex(context, lines, stopOnFailure, index + 1, steps, future);
            return;
        }
        debug(context, "parse ok | phase=" + context.phase()
                + " | line=" + parsed.lineNumber()
                + " | action=" + parsed.actionId()
                + " | control=" + summarize(parsed.control())
                + " | args=" + summarizeMap(parsed.arguments()));
        executeParsed(context, parsed).whenComplete((result, throwable) -> {
            ActionResult finalResult = throwable == null
                    ? (result == null ? ActionResult.ok() : result)
                    : failureResult(parsed.actionId(), throwable);
            steps.add(new ActionStepResult(parsed.lineNumber(), parsed.rawLine(), parsed.actionId(), finalResult));
            debug(context, "step result | phase=" + context.phase()
                    + " | line=" + parsed.lineNumber()
                    + " | action=" + parsed.actionId()
                    + " | success=" + finalResult.success()
                    + " | error=" + Texts.toStringSafe(finalResult.errorMessage()));
            if (!finalResult.success() && !parsed.control().ignoreFailure() && stopOnFailure) {
                debug(context, "batch stop | phase=" + context.phase()
                        + " | line=" + parsed.lineNumber()
                        + " | action=" + parsed.actionId());
                future.complete(new ActionBatchResult(false, List.copyOf(steps)));
                return;
            }
            executeIndex(context, lines, stopOnFailure, index + 1, steps, future);
        });
    }

    private CompletableFuture<ActionResult> executeParsed(ActionContext context, ParsedActionLine parsed) {
        RegisteredAction registration = registry.getRegistered(parsed.actionId());
        if (registration == null) {
            ActionResult result = missingActionResult(parsed.actionId());
            debug(context, "action missing | line=" + parsed.lineNumber()
                    + " | action=" + parsed.actionId()
                    + " | error=" + result.errorMessage());
            return CompletableFuture.completedFuture(result);
        }
        return prepareControls(context, registration, parsed)
                .thenCompose(preparation -> {
                    if (preparation.failure() != null) {
                        return CompletableFuture.completedFuture(preparation.failure());
                    }
                    if ("usetemplate".equals(parsed.actionId())) {
                        return executeTemplate(context, registration, parsed.arguments(), preparation.delayTicks());
                    }
                    return executeRegistered(
                            context,
                            registration,
                            parsed.arguments(),
                            preparation.delayTicks(),
                            preparation.delayTicks() <= 0L);
                });
    }

    private CompletableFuture<ControlPreparation> prepareControls(ActionContext context,
            RegisteredAction registration,
            ParsedActionLine parsed) {
        return dispatchScheduler.dispatch(
                registration.owner(),
                Action.contextualTarget(context),
                0L,
                "action-control:" + parsed.actionId(),
                () -> CompletableFuture.completedFuture(prepareControlsOnOwnedDomain(context, parsed)))
                .exceptionally(throwable -> new ControlPreparation(0L,
                        failureResult(parsed.actionId(), throwable)));
    }

    private ControlPreparation prepareControlsOnOwnedDomain(ActionContext context, ParsedActionLine parsed) {
        String condition = resolveValue(context, parsed.control().condition());
        if (Texts.isNotBlank(condition)) {
            Boolean passes = ConditionEvaluator.evaluateSingle(condition, value -> resolveValue(context, value));
            debug(context, "control condition | line=" + parsed.lineNumber()
                    + " | action=" + parsed.actionId()
                    + " | condition=" + summarize(condition)
                    + " | passes=" + passes);
            if (passes == null) {
                return ControlPreparation.failure(ActionResult.failure(
                        ActionErrorType.INVALID_ARGUMENT, "Invalid @if expression: " + condition));
            }
            if (!passes) {
                return ControlPreparation.failure(ActionResult.skipped("Condition did not pass."));
            }
        }
        String chanceRaw = resolveValue(context, parsed.control().chance());
        if (Texts.isNotBlank(chanceRaw)) {
            long chanceThreshold = ActionParsers.parseChanceThreshold(chanceRaw);
            if (chanceThreshold < 0L || chanceThreshold > ActionParsers.chanceDenominator()) {
                debug(context, "control chance invalid | line=" + parsed.lineNumber()
                        + " | action=" + parsed.actionId()
                        + " | chance=" + chanceRaw);
                return ControlPreparation.failure(ActionResult.failure(
                        ActionErrorType.INVALID_ARGUMENT, "Invalid @chance value: " + chanceRaw));
            }
            long roll = ThreadLocalRandom.current().nextLong(ActionParsers.chanceDenominator());
            boolean passes = chanceThreshold > 0L && roll < chanceThreshold;
            debug(context, "control chance | line=" + parsed.lineNumber()
                    + " | action=" + parsed.actionId()
                    + " | chance=" + chanceRaw
                    + " | threshold=" + chanceThreshold
                    + " | roll=" + roll
                    + " | passes=" + passes);
            if (!passes) {
                return ControlPreparation.failure(ActionResult.skipped("Chance did not pass."));
            }
        }
        long delay = 0L;
        String delayRaw = resolveValue(context, parsed.control().delay());
        if (Texts.isNotBlank(delayRaw)) {
            delay = ActionParsers.parseTicks(delayRaw);
            debug(context, "control delay | line=" + parsed.lineNumber()
                    + " | action=" + parsed.actionId()
                    + " | raw=" + delayRaw
                    + " | ticks=" + delay);
            if (delay < 0L) {
                return ControlPreparation.failure(ActionResult.failure(
                        ActionErrorType.INVALID_ARGUMENT, "Invalid @delay value: " + delayRaw));
            }
        }
        return new ControlPreparation(delay, null);
    }

    private CompletableFuture<ActionResult> executeTemplate(ActionContext context,
            RegisteredAction registration,
            Map<String, String> arguments,
            long delayTicks) {
        return invocationPlanner.plan(registration, context, arguments)
                .thenCompose(plan -> {
                    if (!plan.valid()) {
                        return CompletableFuture.completedFuture(planFailure(plan));
                    }
                    debug(context, "dispatch template | action=" + registration.action().id()
                            + " | delay=" + delayTicks
                            + " | args=" + summarizeMap(plan.arguments()));
                    return dispatchScheduler.dispatch(
                            registration.owner(),
                            plan.target(),
                            delayTicks,
                            registration.action().id(),
                            () -> CompletableFuture.completedFuture(ActionResult.ok()))
                            .thenCompose(result -> result.success()
                                    ? templateProcessor.execute(
                                            context,
                                            plan.arguments(),
                                            (nextContext, lines) -> executeAll(nextContext, lines, true))
                                    : CompletableFuture.completedFuture(result));
                })
                .exceptionally(throwable -> failureResult(registration.action().id(), throwable));
    }

    private CompletableFuture<ActionResult> executeRegistered(ActionContext context,
            RegisteredAction registration,
            Map<String, String> arguments,
            long delayTicks,
            boolean applyTimeout) {
        Action action = registration.action();
        CompletableFuture<ActionResult> future = invocationPlanner.plan(registration, context, arguments)
                .thenCompose(plan -> {
                    if (!plan.valid()) {
                        return CompletableFuture.completedFuture(planFailure(plan));
                    }
                    debug(context, "dispatch action | action=" + action.id()
                            + " | domain=" + plan.target().domain()
                            + " | owner=" + registration.ownerKey()
                            + " | delay=" + delayTicks
                            + " | timeout=" + action.timeoutMillis()
                            + " | args=" + summarizeMap(plan.arguments()));
                    return dispatchScheduler.dispatch(
                            registration.owner(),
                            plan.target(),
                            delayTicks,
                            action.id(),
                            () -> executeActionWithTimeout(context, action, plan.arguments()));
                })
                .exceptionally(throwable -> failureResult(action.id(), throwable));
        return applyTimeout
                ? ActionFutureSupport.withTimeout(context, action.id(), action.timeoutMillis(), future)
                : future;
    }

    private CompletionStage<ActionResult> executeActionWithTimeout(ActionContext context,
            Action action,
            Map<String, String> resolved) {
        return ActionFutureSupport.withTimeout(
                context,
                action.id(),
                action.timeoutMillis(),
                safeExecuteAsync(context, action, resolved).toCompletableFuture());
    }

    private CompletionStage<ActionResult> safeExecuteAsync(ActionContext context,
            Action action,
            Map<String, String> resolved) {
        debug(context, "safe execute start | phase=" + context.phase()
                + " | action=" + action.id()
                + " | args=" + summarizeMap(resolved));
        try {
            CompletionStage<ActionResult> stage = action.executeAsync(context, resolved);
            if (stage == null) {
                return CompletableFuture.completedFuture(ActionResult.failure(
                        ActionErrorType.EXECUTION_EXCEPTION,
                        "Action returned a null completion stage."));
            }
            return stage.handle((result, throwable) -> {
                ActionResult finalResult = throwable == null
                        ? (result == null ? ActionResult.ok() : result)
                        : failureResult(action.id(), throwable);
                debug(context, "safe execute result | phase=" + context.phase()
                        + " | action=" + action.id()
                        + " | success=" + finalResult.success()
                        + " | error=" + Texts.toStringSafe(finalResult.errorMessage()));
                if (!finalResult.success()) {
                    warnExecutionFailure(action.id(), finalResult.errorMessage());
                }
                return finalResult;
            });
        } catch (Throwable throwable) {
            ActionResult failure = failureResult(action.id(), throwable);
            warnExecutionFailure(action.id(), failure.errorMessage());
            return CompletableFuture.completedFuture(failure);
        }
    }

    private ActionResult planFailure(ActionInvocationPlan plan) {
        if (plan == null) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION,
                    "Action invocation could not be planned.");
        }
        if (plan.failure() != null) {
            return plan.failure();
        }
        if (plan.target() != null && plan.target().failure() != null) {
            return plan.target().failure();
        }
        return ActionResult.failure(ActionErrorType.INVALID_STATE,
                "Action invocation could not be planned.");
    }

    private String resolveValue(ActionContext context, String raw) {
        return Texts.isBlank(raw) ? raw : placeholderRegistry.resolve(context, raw);
    }

    private ActionResult missingActionResult(String actionId) {
        return ActionResult.failure(ActionErrorType.ACTION_NOT_FOUND, "Action not found: " + actionId);
    }

    private ActionResult failureResult(String actionId, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String message = cause == null ? "Unknown action execution failure." : cause.getMessage();
        return ActionResult.failure(
                ActionErrorType.EXECUTION_EXCEPTION,
                "Action '" + Texts.toStringSafe(actionId) + "' failed: " + Texts.toStringSafe(message));
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null && cause.getCause() != null
                && (cause instanceof CompletionException || cause instanceof ExecutionException)) {
            cause = cause.getCause();
        }
        return cause;
    }

    private void warnExecutionFailure(String actionId, String error) {
        LogMessages messages = messages();
        if (messages != null) {
            messages.warning("action.execute_failed", Map.of(
                    "action", Texts.toStringSafe(actionId),
                    "error", Texts.toStringSafe(error)));
        }
    }

    private void debug(ActionContext context, String message) {
        DebugLogger debugLogger = resolveDebugLogger(context);
        if (debugLogger != null) {
            debugLogger.logRaw(DEBUG_MODULE, context == null ? null : context.player(), message);
        }
    }

    private DebugLogger resolveDebugLogger(ActionContext context) {
        DebugLogger sourceDebug = debugLoggerOf(context == null ? null : context.sourcePlugin());
        return sourceDebug == null ? debugLoggerOf(plugin) : sourceDebug;
    }

    private DebugLogger debugLoggerOf(Plugin candidate) {
        if (candidate instanceof AbstractEmakiPlugin emakiPlugin) {
            return emakiPlugin.debugLogger();
        }
        if (candidate instanceof EmakiCoreLibPlugin coreLibPlugin) {
            return coreLibPlugin.debugLogger();
        }
        return null;
    }

    private String summarizeMap(Map<String, ?> values) {
        if (values == null || values.isEmpty()) {
            return "{}";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            parts.add(entry.getKey() + "=" + summarize(entry.getValue()));
            if (parts.size() >= 8) {
                parts.add("...");
                break;
            }
        }
        return "{" + String.join(", ", parts) + "}";
    }

    private String summarize(Object value) {
        if (value == null) {
            return "";
        }
        String text = Texts.toStringSafe(value);
        return text.length() <= 160 ? text : text.substring(0, 157) + "...";
    }

    private LogMessages messages() {
        if (plugin instanceof LogMessagesProvider provider) {
            return provider.messageService();
        }
        return null;
    }

    private record ControlPreparation(long delayTicks, ActionResult failure) {

        private static ControlPreparation failure(ActionResult failure) {
            return new ControlPreparation(0L, failure);
        }
    }
}
