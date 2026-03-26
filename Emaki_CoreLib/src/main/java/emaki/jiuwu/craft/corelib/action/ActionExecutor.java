package emaki.jiuwu.craft.corelib.action;

import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class ActionExecutor {

    private static final int MAX_TEMPLATE_DEPTH = 8;

    private final Plugin plugin;
    private final ActionRegistry registry;
    private final ActionLineParser lineParser;
    private final PlaceholderRegistry placeholderRegistry;
    private final ActionTemplateRegistry templateRegistry;

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
    }

    public CompletableFuture<ActionResult> execute(ActionContext context, String actionId, Map<String, String> arguments) {
        Action action = registry.get(actionId);
        if (action == null) {
            return CompletableFuture.completedFuture(ActionResult.failure(ActionErrorType.ACTION_NOT_FOUND, "Action not found: " + actionId));
        }
        Map<String, String> resolved = resolveArguments(context, arguments);
        ActionResult validation = action.validate(resolved);
        if (!validation.success()) {
            return CompletableFuture.completedFuture(validation);
        }
        return dispatch(0L, () -> safeExecute(context, action, resolved));
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
            if (Math.random() > chance) {
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
        if ("use_template".equals(parsed.actionId())) {
            Action action = registry.get(parsed.actionId());
            if (action == null) {
                return CompletableFuture.completedFuture(ActionResult.failure(ActionErrorType.ACTION_NOT_FOUND, "Action not found: " + parsed.actionId()));
            }
            ActionResult validation = action.validate(resolved);
            if (!validation.success()) {
                return CompletableFuture.completedFuture(validation);
            }
            return dispatch(delay, () -> null).thenCompose(ignored -> executeTemplate(context, resolved));
        }
        return dispatch(delay, () -> executeAction(context, parsed.actionId(), resolved));
    }

    private CompletableFuture<ActionResult> executeTemplate(ActionContext context, Map<String, String> arguments) {
        String name = arguments.get("name");
        List<String> lines = templateRegistry.get(name);
        if (lines == null) {
            return CompletableFuture.completedFuture(ActionResult.failure(ActionErrorType.TEMPLATE_NOT_FOUND, "Template not found: " + name));
        }
        int depth = resolveTemplateDepth(context);
        if (depth >= MAX_TEMPLATE_DEPTH) {
            return CompletableFuture.completedFuture(
                ActionResult.failure(ActionErrorType.INVALID_STATE, "Template expansion exceeded max depth of " + MAX_TEMPLATE_DEPTH + ".")
            );
        }
        Map<String, String> templateValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            if (Texts.lower(entry.getKey()).startsWith("with.")) {
                templateValues.put("template_" + Texts.lower(entry.getKey()).substring("with.".length()), entry.getValue());
            }
        }
        ActionContext nextContext = (context == null ? ActionContext.create(plugin, null, "template", false, false) : context)
            .withPlaceholders(templateValues)
            .withAttribute("action_template_depth", depth + 1);
        return executeAll(nextContext, lines, true)
            .thenApply(batch -> batch.success()
                ? ActionResult.ok(Map.of("template", name))
                : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, firstFailureMessage(batch)));
    }

    private String firstFailureMessage(ActionBatchResult batch) {
        ActionStepResult failure = batch.firstFailure();
        return failure == null || failure.result() == null ? "Template execution failed." : failure.result().errorMessage();
    }

    private ActionResult executeAction(ActionContext context, String actionId, Map<String, String> resolved) {
        Action action = registry.get(actionId);
        if (action == null) {
            return ActionResult.failure(ActionErrorType.ACTION_NOT_FOUND, "Action not found: " + actionId);
        }
        ActionResult validation = action.validate(resolved);
        if (!validation.success()) {
            return validation;
        }
        return safeExecute(context, action, resolved);
    }

    private ActionResult safeExecute(ActionContext context, Action action, Map<String, String> resolved) {
        try {
            if (context != null && context.debug() && plugin != null) {
                plugin.getLogger().info("[Action] phase=" + context.phase() + " id=" + action.id() + " args=" + resolved);
            }
            return action.execute(context, resolved);
        } catch (Exception exception) {
            if (plugin != null) {
                plugin.getLogger().warning("[Action] Failed to execute '" + action.id() + "': " + exception.getMessage());
            }
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, exception.getMessage());
        }
    }

    private CompletableFuture<ActionResult> dispatch(long delayTicks, Supplier<ActionResult> task) {
        CompletableFuture<ActionResult> future = new CompletableFuture<>();
        Runnable runnable = () -> {
            try {
                ActionResult result = task.get();
                future.complete(result == null ? ActionResult.ok() : result);
            } catch (Exception exception) {
                future.complete(ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, exception.getMessage()));
            }
        };
        if (plugin == null || !plugin.isEnabled()) {
            future.complete(ActionResult.failure(ActionErrorType.INVALID_STATE, "Source plugin is disabled."));
            return future;
        }
        if (delayTicks <= 0L && Bukkit.isPrimaryThread()) {
            runnable.run();
            return future;
        }
        if (delayTicks <= 0L) {
            plugin.getServer().getScheduler().runTask(plugin, runnable);
            return future;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, runnable, delayTicks);
        return future;
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

    private int resolveTemplateDepth(ActionContext context) {
        if (context == null) {
            return 0;
        }
        Object raw = context.attribute("action_template_depth");
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return ActionParsers.parseInt(Texts.toStringSafe(raw), 0);
    }
}
