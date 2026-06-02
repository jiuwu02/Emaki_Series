package emaki.jiuwu.craft.corelib.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

    private static final long USE_TEMPLATE_DISPATCH_TIMEOUT_MILLIS = Action.DEFAULT_TIMEOUT_MILLIS;
    private static final String DEBUG_MODULE = "action";

    private final Plugin plugin;
    private final ActionRegistry registry;
    private final ActionLineParser lineParser;
    private final PlaceholderRegistry placeholderRegistry;
    private final ActionTemplateRegistry templateRegistry;
    private final ActionTemplateProcessor templateProcessor;
    private final ActionDispatchScheduler dispatchScheduler;

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
        this.templateRegistry = templateRegistry;
        this.templateProcessor = new ActionTemplateProcessor(plugin, templateRegistry);
        this.dispatchScheduler = new ActionDispatchScheduler(plugin, asyncTaskScheduler, performanceMonitor);
    }

    @NotNull
    public CompletableFuture<ActionResult> execute(@NotNull ActionContext context,
            @NotNull String actionId,
            @Nullable Map<String, String> arguments) {
        debug(context, "execute direct | phase=" + context.phase() + " | action=" + actionId + " | rawArgs=" + summarizeMap(arguments));
        PlaceholderRenderer.debugVariables(PlaceholderRenderer.contextVariables(context), resolveDebugLogger(context), context.player(), "action.direct." + actionId);
        Action action = registry.get(actionId);
        if (action == null) {
            ActionResult result = missingActionResult(actionId);
            debug(context, "execute direct missing | action=" + actionId + " | error=" + result.errorMessage());
            return CompletableFuture.completedFuture(result);
        }
        Map<String, String> resolved = resolveArguments(context, arguments);
        debug(context, "execute direct resolved | action=" + actionId + " | args=" + summarizeMap(resolved));
        ActionResult validation = action.validate(resolved);
        if (!validation.success()) {
            debug(context, "execute direct validation failed | action=" + actionId + " | error=" + validation.errorMessage());
            return CompletableFuture.completedFuture(validation);
        }
        return ActionFutureSupport.withTimeout(
                context,
                actionId,
                dispatchScheduler.dispatch(0L, actionId, action.executionMode(), action.timeoutMillis(), () -> safeExecute(context, action, resolved))
                        .whenComplete((result, throwable) -> debug(context, "execute direct result | action=" + actionId
                        + " | success=" + (throwable == null && result != null && result.success())
                        + " | error=" + (throwable == null ? (result == null ? "" : result.errorMessage()) : throwable.getMessage())))
        );
    }

    @NotNull
    public CompletableFuture<ActionBatchResult> executeAll(@NotNull ActionContext context,
            @Nullable List<String> lines,
            boolean stopOnFailure) {
        List<String> safeLines = lines == null ? List.of() : lines;
        debug(context, "execute batch start | phase=" + context.phase()
                + " | lines=" + safeLines.size()
                + " | stopOnFailure=" + stopOnFailure);
        PlaceholderRenderer.debugVariables(PlaceholderRenderer.contextVariables(context), resolveDebugLogger(context), context.player(), "action." + context.phase());
        CompletableFuture<ActionBatchResult> future = new CompletableFuture<>();
        executeIndex(context, safeLines, stopOnFailure, 0, new ArrayList<>(), future);
        return future.whenComplete((batch, throwable) -> debug(context, "execute batch result | phase=" + context.phase()
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
            ActionStepResult step = new ActionStepResult(
                    exception.lineNumber(),
                    exception.rawLine(),
                    "",
                    ActionResult.failure(ActionErrorType.SYNTAX_ERROR, exception.getMessage())
            );
            steps.add(step);
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
                    ? result
                    : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, throwable.getMessage());
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
        String condition = resolveValue(context, parsed.control().condition());
        if (Texts.isNotBlank(condition)) {
            Boolean passes = ConditionEvaluator.evaluateSingle(condition, value -> resolveValue(context, value));
            debug(context, "control condition | line=" + parsed.lineNumber()
                    + " | action=" + parsed.actionId()
                    + " | condition=" + summarize(condition)
                    + " | passes=" + passes);
            if (passes == null) {
                return CompletableFuture.completedFuture(ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Invalid @if expression: " + condition));
            }
            if (!passes) {
                return CompletableFuture.completedFuture(ActionResult.skipped("Condition did not pass."));
            }
        }
        String chanceRaw = resolveValue(context, parsed.control().chance());
        if (Texts.isNotBlank(chanceRaw)) {
            long chanceThreshold = ActionParsers.parseChanceThreshold(chanceRaw);
            if (chanceThreshold < 0L || chanceThreshold > ActionParsers.chanceDenominator()) {
                debug(context, "control chance invalid | line=" + parsed.lineNumber()
                        + " | action=" + parsed.actionId()
                        + " | chance=" + chanceRaw);
                return CompletableFuture.completedFuture(ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Invalid @chance value: " + chanceRaw));
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
                return CompletableFuture.completedFuture(ActionResult.skipped("Chance did not pass."));
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
                return CompletableFuture.completedFuture(ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Invalid @delay value: " + delayRaw));
            }
        }
        Map<String, String> resolved = resolveArguments(context, parsed.arguments());
        debug(context, "arguments resolved | line=" + parsed.lineNumber()
                + " | action=" + parsed.actionId()
                + " | args=" + summarizeMap(resolved));
        Action action = registry.get(parsed.actionId());
        if (action == null) {
            ActionResult result = missingActionResult(parsed.actionId());
            debug(context, "action missing | line=" + parsed.lineNumber()
                    + " | action=" + parsed.actionId()
                    + " | error=" + result.errorMessage());
            return CompletableFuture.completedFuture(result);
        }
        ActionResult validation = action.validate(resolved);
        if (!validation.success()) {
            debug(context, "validation failed | line=" + parsed.lineNumber()
                    + " | action=" + parsed.actionId()
                    + " | error=" + validation.errorMessage());
            return CompletableFuture.completedFuture(validation);
        }
        if ("usetemplate".equals(parsed.actionId())) {
            debug(context, "dispatch template | line=" + parsed.lineNumber()
                    + " | delay=" + delay
                    + " | args=" + summarizeMap(resolved));
            return dispatchScheduler.dispatch(delay, parsed.actionId(), ActionExecutionMode.SYNC, USE_TEMPLATE_DISPATCH_TIMEOUT_MILLIS, () -> null)
                    .thenCompose(ignored -> templateProcessor.execute(context, resolved, (nextContext, lines) -> executeAll(nextContext, lines, true)));
        }
        debug(context, "dispatch action | line=" + parsed.lineNumber()
                + " | action=" + parsed.actionId()
                + " | mode=" + action.executionMode()
                + " | delay=" + delay
                + " | timeout=" + action.timeoutMillis());
        CompletableFuture<ActionResult> future = dispatchScheduler.dispatch(
                delay,
                parsed.actionId(),
                action.executionMode(),
                action.timeoutMillis(),
                () -> safeExecute(context, action, resolved)
        );
        return delay > 0L ? future : ActionFutureSupport.withTimeout(context, parsed.actionId(), future);
    }

    private ActionResult safeExecute(ActionContext context, Action action, Map<String, String> resolved) {
        try {
            debug(context, "safe execute start | phase=" + context.phase()
                    + " | action=" + action.id()
                    + " | args=" + summarizeMap(resolved));
            ActionResult result = action.execute(context, resolved);
            debug(context, "safe execute result | phase=" + context.phase()
                    + " | action=" + action.id()
                    + " | success=" + (result != null && result.success())
                    + " | error=" + (result == null ? "" : Texts.toStringSafe(result.errorMessage())));
            return result;
        } catch (Exception exception) {
            debug(context, "safe execute exception | phase=" + context.phase()
                    + " | action=" + action.id()
                    + " | error=" + exception.getMessage());
            LogMessages messages = messages();
            if (messages != null) {
                messages.warning("action.execute_failed", Map.of(
                        "action", action.id(),
                        "error", Texts.toStringSafe(exception.getMessage())
                ));
            }
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, exception.getMessage());
        }
    }

    private Map<String, String> resolveArguments(ActionContext context, Map<String, String> arguments) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (arguments != null) {
            for (Map.Entry<String, String> entry : arguments.entrySet()) {
                resolved.put(entry.getKey(), resolveValue(context, entry.getValue()));
            }
        }
        return resolved;
    }

    private String resolveValue(ActionContext context, String raw) {
        return Texts.isBlank(raw) ? raw : placeholderRegistry.resolve(context, raw);
    }

    private ActionResult missingActionResult(String actionId) {
        String normalized = Texts.toStringSafe(actionId).replace("_", "");
        if (!normalized.equals(actionId) && registry.get(normalized) != null) {
            return ActionResult.failure(ActionErrorType.ACTION_NOT_FOUND, "Action not found: " + actionId + ". Use '" + normalized + "' instead.");
        }
        return ActionResult.failure(ActionErrorType.ACTION_NOT_FOUND, "Action not found: " + actionId);
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

}
