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

import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.monitor.PerformanceMonitor;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Executes parsed action lines against a runtime {@link ActionContext}.
 */
public final class ActionExecutor {

    private static final long USE_TEMPLATE_DISPATCH_TIMEOUT_MILLIS = Action.DEFAULT_TIMEOUT_MILLIS;

    private final Plugin plugin;
    private final ActionRegistry registry;
    private final ActionLineParser lineParser;
    private final PlaceholderRegistry placeholderRegistry;
    private final ActionTemplateRegistry templateRegistry;
    private final ActionTemplateProcessor templateProcessor;
    private final ActionDispatchScheduler dispatchScheduler;

    /**
     * Creates an executor without async scheduling support.
     *
     * @param plugin the owning plugin
     * @param registry the action registry
     * @param lineParser the parser used for action lines
     * @param placeholderRegistry the placeholder resolver registry
     * @param templateRegistry the template registry
     */
    public ActionExecutor(@NotNull Plugin plugin,
            @NotNull ActionRegistry registry,
            @NotNull ActionLineParser lineParser,
            @NotNull PlaceholderRegistry placeholderRegistry,
            @NotNull ActionTemplateRegistry templateRegistry) {
        this(plugin, registry, lineParser, placeholderRegistry, templateRegistry, null, null);
    }

    /**
     * Creates an executor with optional async scheduling support.
     *
     * @param plugin the owning plugin
     * @param registry the action registry
     * @param lineParser the parser used for action lines
     * @param placeholderRegistry the placeholder resolver registry
     * @param templateRegistry the template registry
     * @param asyncTaskScheduler the optional async task scheduler
     * @param performanceMonitor the optional performance monitor
     */
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

    /**
     * Executes a single action by id.
     *
     * @param context the action runtime context
     * @param actionId the action id to execute
     * @param arguments the optional resolved arguments
     * @return a future that completes with the action result
     */
    @NotNull
    public CompletableFuture<ActionResult> execute(@NotNull ActionContext context,
            @NotNull String actionId,
            @Nullable Map<String, String> arguments) {
        Action action = registry.get(actionId);
        if (action == null) {
            return CompletableFuture.completedFuture(missingActionResult(actionId));
        }
        Map<String, String> resolved = resolveArguments(context, arguments);
        ActionResult validation = action.validate(resolved);
        if (!validation.success()) {
            return CompletableFuture.completedFuture(validation);
        }
        return ActionFutureSupport.withTimeout(
                context,
                actionId,
                dispatchScheduler.dispatch(0L, actionId, action.executionMode(), action.timeoutMillis(), () -> safeExecute(context, action, resolved))
        );
    }

    /**
     * Executes a batch of action lines in order.
     *
     * @param context the action runtime context
     * @param lines the raw action lines to execute
     * @param stopOnFailure whether execution should stop on the first non-ignored failure
     * @return a future that completes with the batch result
     */
    @NotNull
    public CompletableFuture<ActionBatchResult> executeAll(@NotNull ActionContext context,
            @Nullable List<String> lines,
            boolean stopOnFailure) {
        List<String> safeLines = lines == null ? List.of() : lines;
        CompletableFuture<ActionBatchResult> future = new CompletableFuture<>();
        executeIndex(context, safeLines, stopOnFailure, 0, new ArrayList<>(), future);
        return future;
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
        try {
            parsed = lineParser.parse(index + 1, lines.get(index));
        } catch (ActionSyntaxException exception) {
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
            executeIndex(context, lines, stopOnFailure, index + 1, steps, future);
            return;
        }
        executeParsed(context, parsed).whenComplete((result, throwable) -> {
            ActionResult finalResult = throwable == null
                    ? result
                    : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, throwable.getMessage());
            steps.add(new ActionStepResult(parsed.lineNumber(), parsed.rawLine(), parsed.actionId(), finalResult));
            if (!finalResult.success() && !parsed.control().ignoreFailure() && stopOnFailure) {
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
                return CompletableFuture.completedFuture(ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Invalid @chance value: " + chanceRaw));
            }
            if (chanceThreshold <= 0L
                    || ThreadLocalRandom.current().nextLong(ActionParsers.chanceDenominator()) >= chanceThreshold) {
                return CompletableFuture.completedFuture(ActionResult.skipped("Chance did not pass."));
            }
        }
        long delay = 0L;
        String delayRaw = resolveValue(context, parsed.control().delay());
        if (Texts.isNotBlank(delayRaw)) {
            delay = ActionParsers.parseTicks(delayRaw);
            if (delay < 0L) {
                return CompletableFuture.completedFuture(ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Invalid @delay value: " + delayRaw));
            }
        }
        Map<String, String> resolved = resolveArguments(context, parsed.arguments());
        Action action = registry.get(parsed.actionId());
        if (action == null) {
            return CompletableFuture.completedFuture(missingActionResult(parsed.actionId()));
        }
        ActionResult validation = action.validate(resolved);
        if (!validation.success()) {
            return CompletableFuture.completedFuture(validation);
        }
        if ("usetemplate".equals(parsed.actionId())) {
            return dispatchScheduler.dispatch(delay, parsed.actionId(), ActionExecutionMode.SYNC, USE_TEMPLATE_DISPATCH_TIMEOUT_MILLIS, () -> null)
                    .thenCompose(ignored -> templateProcessor.execute(context, resolved, (nextContext, lines) -> executeAll(nextContext, lines, true)));
        }
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
            return action.execute(context, resolved);
        } catch (Exception exception) {
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

    private LogMessages messages() {
        if (plugin instanceof LogMessagesProvider provider) {
            return provider.messageService();
        }
        return null;
    }

}
