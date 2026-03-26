package emaki.jiuwu.craft.corelib.operation;

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

public final class OperationExecutor {

    private static final int MAX_TEMPLATE_DEPTH = 8;

    private final Plugin plugin;
    private final OperationRegistry registry;
    private final OperationLineParser lineParser;
    private final PlaceholderRegistry placeholderRegistry;
    private final OperationTemplateRegistry templateRegistry;

    public OperationExecutor(Plugin plugin,
                             OperationRegistry registry,
                             OperationLineParser lineParser,
                             PlaceholderRegistry placeholderRegistry,
                             OperationTemplateRegistry templateRegistry) {
        this.plugin = plugin;
        this.registry = registry;
        this.lineParser = lineParser;
        this.placeholderRegistry = placeholderRegistry;
        this.templateRegistry = templateRegistry;
    }

    public CompletableFuture<OperationResult> execute(OperationContext context, String operationId, Map<String, String> arguments) {
        Operation operation = registry.get(operationId);
        if (operation == null) {
            return CompletableFuture.completedFuture(OperationResult.failure(OperationErrorType.OPERATION_NOT_FOUND, "Operation not found: " + operationId));
        }
        Map<String, String> resolved = resolveArguments(context, arguments);
        OperationResult validation = operation.validate(resolved);
        if (!validation.success()) {
            return CompletableFuture.completedFuture(validation);
        }
        return dispatch(0L, () -> safeExecute(context, operation, resolved));
    }

    public CompletableFuture<OperationBatchResult> executeAll(OperationContext context, List<String> lines, boolean stopOnFailure) {
        List<String> safeLines = lines == null ? List.of() : lines;
        CompletableFuture<OperationBatchResult> future = new CompletableFuture<>();
        executeIndex(context, safeLines, stopOnFailure, 0, new ArrayList<>(), future);
        return future;
    }

    private void executeIndex(OperationContext context,
                              List<String> lines,
                              boolean stopOnFailure,
                              int index,
                              List<OperationStepResult> steps,
                              CompletableFuture<OperationBatchResult> future) {
        if (index >= lines.size()) {
            future.complete(new OperationBatchResult(true, List.copyOf(steps)));
            return;
        }
        ParsedOperationLine parsed;
        try {
            parsed = lineParser.parse(index + 1, lines.get(index));
        } catch (OperationSyntaxException exception) {
            OperationStepResult step = new OperationStepResult(
                exception.lineNumber(),
                exception.rawLine(),
                "",
                OperationResult.failure(OperationErrorType.SYNTAX_ERROR, exception.getMessage())
            );
            steps.add(step);
            future.complete(new OperationBatchResult(false, List.copyOf(steps)));
            return;
        }
        if (parsed == null) {
            executeIndex(context, lines, stopOnFailure, index + 1, steps, future);
            return;
        }
        executeParsed(context, parsed).whenComplete((result, throwable) -> {
            OperationResult finalResult = throwable == null
                ? result
                : OperationResult.failure(OperationErrorType.EXECUTION_EXCEPTION, throwable.getMessage());
            steps.add(new OperationStepResult(parsed.lineNumber(), parsed.rawLine(), parsed.operationId(), finalResult));
            if (!finalResult.success() && !parsed.control().ignoreFailure() && stopOnFailure) {
                future.complete(new OperationBatchResult(false, List.copyOf(steps)));
                return;
            }
            executeIndex(context, lines, stopOnFailure, index + 1, steps, future);
        });
    }

    private CompletableFuture<OperationResult> executeParsed(OperationContext context, ParsedOperationLine parsed) {
        String condition = resolveValue(context, parsed.control().condition());
        if (Texts.isNotBlank(condition)) {
            Boolean passes = ConditionEvaluator.evaluateSingle(condition, value -> resolveValue(context, value));
            if (passes == null) {
                return CompletableFuture.completedFuture(OperationResult.failure(OperationErrorType.INVALID_ARGUMENT, "Invalid @if expression: " + condition));
            }
            if (!passes) {
                return CompletableFuture.completedFuture(OperationResult.skipped("Condition did not pass."));
            }
        }
        String chanceRaw = resolveValue(context, parsed.control().chance());
        if (Texts.isNotBlank(chanceRaw)) {
            double chance = OperationParsers.parseChance(chanceRaw);
            if (chance < 0D || chance > 1D) {
                return CompletableFuture.completedFuture(OperationResult.failure(OperationErrorType.INVALID_ARGUMENT, "Invalid @chance value: " + chanceRaw));
            }
            if (Math.random() > chance) {
                return CompletableFuture.completedFuture(OperationResult.skipped("Chance did not pass."));
            }
        }
        long delay = 0L;
        String delayRaw = resolveValue(context, parsed.control().delay());
        if (Texts.isNotBlank(delayRaw)) {
            delay = OperationParsers.parseTicks(delayRaw);
            if (delay < 0L) {
                return CompletableFuture.completedFuture(OperationResult.failure(OperationErrorType.INVALID_ARGUMENT, "Invalid @delay value: " + delayRaw));
            }
        }
        Map<String, String> resolved = resolveArguments(context, parsed.arguments());
        if ("use_template".equals(parsed.operationId())) {
            Operation operation = registry.get(parsed.operationId());
            if (operation == null) {
                return CompletableFuture.completedFuture(OperationResult.failure(OperationErrorType.OPERATION_NOT_FOUND, "Operation not found: " + parsed.operationId()));
            }
            OperationResult validation = operation.validate(resolved);
            if (!validation.success()) {
                return CompletableFuture.completedFuture(validation);
            }
            return dispatch(delay, () -> null).thenCompose(ignored -> executeTemplate(context, resolved));
        }
        return dispatch(delay, () -> executeOperation(context, parsed.operationId(), resolved));
    }

    private CompletableFuture<OperationResult> executeTemplate(OperationContext context, Map<String, String> arguments) {
        String name = arguments.get("name");
        List<String> lines = templateRegistry.get(name);
        if (lines == null) {
            return CompletableFuture.completedFuture(OperationResult.failure(OperationErrorType.TEMPLATE_NOT_FOUND, "Template not found: " + name));
        }
        int depth = resolveTemplateDepth(context);
        if (depth >= MAX_TEMPLATE_DEPTH) {
            return CompletableFuture.completedFuture(
                OperationResult.failure(OperationErrorType.INVALID_STATE, "Template expansion exceeded max depth of " + MAX_TEMPLATE_DEPTH + ".")
            );
        }
        Map<String, String> templateValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            if (Texts.lower(entry.getKey()).startsWith("with.")) {
                templateValues.put("template_" + Texts.lower(entry.getKey()).substring("with.".length()), entry.getValue());
            }
        }
        OperationContext nextContext = (context == null ? OperationContext.create(plugin, null, "template", false, false) : context)
            .withPlaceholders(templateValues)
            .withAttribute("operation_template_depth", depth + 1);
        return executeAll(nextContext, lines, true)
            .thenApply(batch -> batch.success()
                ? OperationResult.ok(Map.of("template", name))
                : OperationResult.failure(OperationErrorType.EXECUTION_EXCEPTION, firstFailureMessage(batch)));
    }

    private String firstFailureMessage(OperationBatchResult batch) {
        OperationStepResult failure = batch.firstFailure();
        return failure == null || failure.result() == null ? "Template execution failed." : failure.result().errorMessage();
    }

    private OperationResult executeOperation(OperationContext context, String operationId, Map<String, String> resolved) {
        Operation operation = registry.get(operationId);
        if (operation == null) {
            return OperationResult.failure(OperationErrorType.OPERATION_NOT_FOUND, "Operation not found: " + operationId);
        }
        OperationResult validation = operation.validate(resolved);
        if (!validation.success()) {
            return validation;
        }
        return safeExecute(context, operation, resolved);
    }

    private OperationResult safeExecute(OperationContext context, Operation operation, Map<String, String> resolved) {
        try {
            if (context != null && context.debug() && plugin != null) {
                plugin.getLogger().info("[Operation] phase=" + context.phase() + " id=" + operation.id() + " args=" + resolved);
            }
            return operation.execute(context, resolved);
        } catch (Exception exception) {
            if (plugin != null) {
                plugin.getLogger().warning("[Operation] Failed to execute '" + operation.id() + "': " + exception.getMessage());
            }
            return OperationResult.failure(OperationErrorType.EXECUTION_EXCEPTION, exception.getMessage());
        }
    }

    private CompletableFuture<OperationResult> dispatch(long delayTicks, Supplier<OperationResult> task) {
        CompletableFuture<OperationResult> future = new CompletableFuture<>();
        Runnable runnable = () -> {
            try {
                OperationResult result = task.get();
                future.complete(result == null ? OperationResult.ok() : result);
            } catch (Exception exception) {
                future.complete(OperationResult.failure(OperationErrorType.EXECUTION_EXCEPTION, exception.getMessage()));
            }
        };
        if (plugin == null || !plugin.isEnabled()) {
            future.complete(OperationResult.failure(OperationErrorType.INVALID_STATE, "Source plugin is disabled."));
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

    private Map<String, String> resolveArguments(OperationContext context, Map<String, String> arguments) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (arguments != null) {
            for (Map.Entry<String, String> entry : arguments.entrySet()) {
                resolved.put(entry.getKey(), resolveValue(context, entry.getValue()));
            }
        }
        return resolved;
    }

    private String resolveValue(OperationContext context, String raw) {
        return Texts.isBlank(raw) ? raw : placeholderRegistry.resolve(context, raw);
    }

    private int resolveTemplateDepth(OperationContext context) {
        if (context == null) {
            return 0;
        }
        Object raw = context.attribute("operation_template_depth");
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return OperationParsers.parseInt(Texts.toStringSafe(raw), 0);
    }
}
