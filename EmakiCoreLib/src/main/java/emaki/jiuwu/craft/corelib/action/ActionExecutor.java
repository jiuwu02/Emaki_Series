package emaki.jiuwu.craft.corelib.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ActionExecutor {

    private final Plugin plugin;
    private final ActionRegistry registry;
    private final ActionLineParser lineParser;
    private final PlaceholderRegistry placeholderRegistry;
    private final ActionTemplateRegistry templateRegistry;
    private final ActionTemplateProcessor templateProcessor;
    private final ActionDispatchScheduler dispatchScheduler;

    public ActionExecutor(Plugin plugin,
            ActionRegistry registry,
            ActionLineParser lineParser,
            PlaceholderRegistry placeholderRegistry,
            ActionTemplateRegistry templateRegistry) {
        this.plugin = plugin;
        this.registry = registry;
        this.lineParser = lineParser;
        this.placeholderRegistry = placeholderRegistry;
        this.templateRegistry = templateRegistry;
        this.templateProcessor = new ActionTemplateProcessor(plugin, templateRegistry);
        this.dispatchScheduler = new ActionDispatchScheduler(plugin);
    }

    public CompletableFuture<ActionResult> execute(ActionContext context, String actionId, Map<String, String> arguments) {
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

    public CompletableFuture<ActionBatchResult> executeAll(ActionContext context, List<String> lines, boolean stopOnFailure) {
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
            double chance = ActionParsers.parseChance(chanceRaw);
            if (chance < 0D || chance > 1D) {
                return CompletableFuture.completedFuture(ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Invalid @chance value: " + chanceRaw));
            }
            if (ThreadLocalRandom.current().nextDouble() > chance) {
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
        if ("usetemplate".equals(parsed.actionId())) {
            Action action = registry.get(parsed.actionId());
            if (action == null) {
                return CompletableFuture.completedFuture(missingActionResult(parsed.actionId()));
            }
            ActionResult validation = action.validate(resolved);
            if (!validation.success()) {
                return CompletableFuture.completedFuture(validation);
            }
            return dispatchScheduler.dispatch(delay, parsed.actionId(), ActionExecutionMode.SYNC, 30_000L, () -> null)
                    .thenCompose(ignored -> templateProcessor.execute(context, resolved, (nextContext, lines) -> executeAll(nextContext, lines, true)));
        }
        CompletableFuture<ActionResult> future = dispatchScheduler.dispatch(
                delay,
                parsed.actionId(),
                resolveExecutionMode(parsed.actionId()),
                resolveTimeoutMillis(parsed.actionId()),
                () -> executeAction(context, parsed.actionId(), resolved)
        );
        return delay > 0L ? future : ActionFutureSupport.withTimeout(context, parsed.actionId(), future);
    }

    private ActionResult executeAction(ActionContext context, String actionId, Map<String, String> resolved) {
        Action action = registry.get(actionId);
        if (action == null) {
            return missingActionResult(actionId);
        }
        ActionResult validation = action.validate(resolved);
        if (!validation.success()) {
            return validation;
        }
        return safeExecute(context, action, resolved);
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

    private ActionExecutionMode resolveExecutionMode(String actionId) {
        Action action = registry.get(actionId);
        return action == null ? ActionExecutionMode.SYNC : action.executionMode();
    }

    private long resolveTimeoutMillis(String actionId) {
        Action action = registry.get(actionId);
        return action == null ? 30_000L : action.timeoutMillis();
    }
}
