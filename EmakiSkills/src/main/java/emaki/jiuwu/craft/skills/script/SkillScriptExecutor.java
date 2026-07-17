package emaki.jiuwu.craft.skills.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionSyntaxException;
import emaki.jiuwu.craft.corelib.action.ParsedActionLine;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.api.SkillActionErrorType;
import emaki.jiuwu.craft.skills.api.SkillActionExecutionMode;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.skills.api.SkillScriptAction;
import emaki.jiuwu.craft.skills.api.SkillScriptActionRegistry;
import emaki.jiuwu.craft.skills.config.AppConfig;

public final class SkillScriptExecutor {

    private final SkillScriptActionRegistry registry;
    private final ActionLineParser lineParser = new ActionLineParser();

    public SkillScriptExecutor(SkillScriptActionRegistry registry) {
        this.registry = registry;
    }

    public CompletableFuture<SkillActionResult> executePhase(SkillScriptContext context,
            SkillScriptDefinition script,
            SkillScriptPhase phase) {
        EntityDomain domain = new EntityDomain(context);
        return domain.submit(0L, () -> executePhaseOnDomain(domain, context, script, phase));
    }

    public CompletableFuture<SkillActionResult> executeHitOrMissPhase(SkillScriptContext context,
            SkillScriptDefinition script,
            SkillActionResult castResult) {
        EntityDomain domain = new EntityDomain(context);
        return domain.submit(0L, () -> {
            SkillActionResult previous = normalize(castResult);
            if (!previous.success() && script.stopOnFailure()) {
                return CompletableFuture.completedFuture(previous);
            }
            SkillScriptPhase next = context.hasTarget() ? SkillScriptPhase.HIT : SkillScriptPhase.MISS;
            return executePhase(context, script, next);
        });
    }

    public CompletableFuture<SkillActionResult> executeLine(SkillScriptContext context,
            int lineNumber,
            String line) {
        EntityDomain domain = new EntityDomain(context);
        return domain.submit(0L, () -> executeLineOnDomain(domain, context, lineNumber, line));
    }

    private CompletionStage<SkillActionResult> executePhaseOnDomain(EntityDomain domain,
            SkillScriptContext context,
            SkillScriptDefinition script,
            SkillScriptPhase phase) {
        List<String> conditions = script.conditions(phase);
        if (!conditions.isEmpty() && !ConditionEvaluator.evaluate(conditions, "all_of", null,
                text -> resolveText(context, text), true)) {
            return CompletableFuture.completedFuture(SkillActionResult.skipped(
                    "Script phase condition did not pass."));
        }
        List<String> lines = script.lines(phase);
        if (lines.isEmpty()) {
            return CompletableFuture.completedFuture(SkillActionResult.ok());
        }
        AppConfig.ScriptEngineSettings settings = context.plugin().appConfig().scriptEngine();
        int maxLines = Math.min(lines.size(), settings.maxLinesPerPhase());
        List<String> limited = new ArrayList<>(lines.subList(0, maxLines));
        return advancePhase(domain, context, script, limited, 0, SkillActionResult.ok());
    }

    private CompletionStage<SkillActionResult> advancePhase(EntityDomain domain,
            SkillScriptContext context,
            SkillScriptDefinition script,
            List<String> lines,
            int index,
            SkillActionResult previous) {
        return domain.submit(0L, () -> {
            SkillActionResult safePrevious = normalize(previous);
            if (index >= lines.size() || (!safePrevious.success() && script.stopOnFailure())) {
                return CompletableFuture.completedFuture(safePrevious);
            }
            return executeLineOnDomain(domain, context, index + 1, lines.get(index))
                    .thenCompose(result -> advancePhase(domain, context, script, lines, index + 1, result));
        });
    }

    private CompletionStage<SkillActionResult> executeLineOnDomain(EntityDomain domain,
            SkillScriptContext context,
            int lineNumber,
            String line) {
        ParsedActionLine parsed;
        try {
            parsed = lineParser.parse(lineNumber, resolveText(context, line));
        } catch (ActionSyntaxException exception) {
            return CompletableFuture.completedFuture(SkillActionResult.failure(
                    SkillActionErrorType.SYNTAX_ERROR, exception.getMessage()));
        }
        if (parsed == null) {
            return CompletableFuture.completedFuture(SkillActionResult.ok());
        }

        String condition = resolveText(context, parsed.control().condition());
        if (Texts.isNotBlank(condition)) {
            Boolean passes = ConditionEvaluator.evaluateSingle(condition, value -> resolveText(context, value));
            if (passes == null) {
                return CompletableFuture.completedFuture(SkillActionResult.failure(
                        SkillActionErrorType.INVALID_ARGUMENT, "Invalid @if expression: " + condition));
            }
            if (!passes) {
                return CompletableFuture.completedFuture(SkillActionResult.skipped("Condition did not pass."));
            }
        }

        String chanceRaw = resolveText(context, parsed.control().chance());
        if (Texts.isNotBlank(chanceRaw)) {
            long threshold = ActionParsers.parseChanceThreshold(chanceRaw);
            if (threshold < 0L || threshold > ActionParsers.chanceDenominator()) {
                return CompletableFuture.completedFuture(SkillActionResult.failure(
                        SkillActionErrorType.INVALID_ARGUMENT, "Invalid @chance value: " + chanceRaw));
            }
            if (threshold <= 0L
                    || ThreadLocalRandom.current().nextLong(ActionParsers.chanceDenominator()) >= threshold) {
                return CompletableFuture.completedFuture(SkillActionResult.skipped("Chance did not pass."));
            }
        }

        long delay = 0L;
        String delayRaw = resolveText(context, parsed.control().delay());
        if (Texts.isNotBlank(delayRaw)) {
            delay = ActionParsers.parseTicks(delayRaw);
            if (delay < 0L) {
                return CompletableFuture.completedFuture(SkillActionResult.failure(
                        SkillActionErrorType.INVALID_ARGUMENT, "Invalid @delay value: " + delayRaw));
            }
        }

        Map<String, String> arguments = resolveArguments(context, parsed.arguments());
        SkillScriptAction action = registry.get(parsed.actionId());
        if (action == null) {
            return afterLineAsync(domain, context, SkillActionResult.failure(
                    SkillActionErrorType.ACTION_NOT_FOUND,
                    "Skill script action not found: " + parsed.actionId()));
        }

        return domain.submit(delay, () -> invokeActionOnDomain(action, context, arguments))
                .handle((result, throwable) -> throwable == null
                        ? normalize(result)
                        : failure(throwable))
                .thenCompose(result -> afterLineAsync(domain, context, result));
    }

    private CompletionStage<SkillActionResult> invokeActionOnDomain(SkillScriptAction action,
            SkillScriptContext context,
            Map<String, String> arguments) {
        try {
            Plugin owner = registry.ownerOf(safeActionId(action));
            if (owner == null || !owner.isEnabled()) {
                return CompletableFuture.completedFuture(SkillActionResult.failure(
                        SkillActionErrorType.INVALID_STATE,
                        "Skill action owner is disabled: " + safeActionId(action)));
            }
            SkillActionResult validation = action.validate(arguments);
            if (validation == null) {
                return CompletableFuture.completedFuture(SkillActionResult.failure(
                        SkillActionErrorType.EXECUTION_EXCEPTION,
                        "Skill action validation returned no result."));
            }
            if (!validation.success()) {
                return CompletableFuture.completedFuture(validation);
            }
            SkillScriptAction.CancellationToken cancellationToken = new SkillScriptAction.CancellationToken();
            CompletionStage<SkillActionResult> stage = invokeByMode(action, context, arguments, cancellationToken);
            if (stage == null) {
                cancellationToken.cancel();
                return CompletableFuture.completedFuture(SkillActionResult.failure(
                        SkillActionErrorType.EXECUTION_EXCEPTION,
                        "Skill action returned no completion stage."));
            }
            return withTimeout(action, stage, cancellationToken);
        } catch (Throwable throwable) {
            return CompletableFuture.completedFuture(failure(throwable));
        }
    }

    private CompletionStage<SkillActionResult> invokeByMode(SkillScriptAction action,
            SkillScriptContext context,
            Map<String, String> arguments,
            SkillScriptAction.CancellationToken cancellationToken) {
        SkillActionExecutionMode mode = executionMode(action);
        if (mode != SkillActionExecutionMode.ASYNC_IO) {
            return action.executeAsync(context, arguments, cancellationToken);
        }
        if (context == null || context.plugin() == null
                || context.plugin().coreLib() == null
                || context.plugin().coreLib().asyncTaskScheduler() == null) {
            return CompletableFuture.completedFuture(SkillActionResult.failure(
                    SkillActionErrorType.INVALID_STATE,
                    "Async skill action scheduler is unavailable."));
        }
        AsyncTaskScheduler scheduler = context.plugin().coreLib().asyncTaskScheduler();
        return scheduler.supplyAsync(
                "skill-action:" + safeActionId(action),
                AsyncTaskScheduler.TaskPriority.LOW,
                timeoutMillis(action),
                () -> action.executeAsync(context, arguments, cancellationToken))
                .thenCompose(stage -> stage == null
                        ? CompletableFuture.completedFuture(SkillActionResult.failure(
                                SkillActionErrorType.EXECUTION_EXCEPTION,
                                "Skill action returned no completion stage."))
                        : stage);
    }

    private CompletionStage<SkillActionResult> withTimeout(SkillScriptAction action,
            CompletionStage<SkillActionResult> stage,
            SkillScriptAction.CancellationToken cancellationToken) {
        CompletableFuture<SkillActionResult> source = stage.toCompletableFuture();
        long timeoutMillis = Math.max(1L, timeoutMillis(action));
        CompletableFuture<SkillActionResult> guarded = new CompletableFuture<>();
        source.whenComplete((result, error) -> {
            if (error == null) {
                guarded.complete(result == null
                        ? SkillActionResult.failure(SkillActionErrorType.EXECUTION_EXCEPTION,
                                "Skill action completed without a result.")
                        : result);
                return;
            }
            Throwable cause = unwrap(error);
            if (cause instanceof CancellationException) {
                cancellationToken.cancel();
                guarded.complete(SkillActionResult.failure(SkillActionErrorType.CANCELLED,
                        "Skill action was cancelled: " + safeActionId(action)));
                return;
            }
            String message = cause == null || Texts.isBlank(cause.getMessage())
                    ? "unknown error"
                    : cause.getMessage();
            guarded.complete(SkillActionResult.failure(SkillActionErrorType.EXECUTION_EXCEPTION,
                    "Skill action failed: " + message));
        });
        CompletableFuture.delayedExecutor(timeoutMillis, TimeUnit.MILLISECONDS).execute(() -> {
            if (!guarded.isDone()) {
                cancellationToken.cancel();
                boolean timedOut = guarded.complete(SkillActionResult.failure(SkillActionErrorType.TIMEOUT,
                        "Skill action timed out after " + timeoutMillis + "ms: " + safeActionId(action)));
                if (timedOut) {
                    source.cancel(true);
                }
            }
        });
        guarded.whenComplete((ignored, error) -> {
            if (guarded.isCancelled()) {
                cancellationToken.cancel();
                source.cancel(true);
            }
        });
        return guarded;
    }

    private static SkillActionExecutionMode executionMode(SkillScriptAction action) {
        try {
            SkillActionExecutionMode mode = action == null ? null : action.executionMode();
            return mode == null ? SkillActionExecutionMode.SYNC : mode;
        } catch (Throwable throwable) {
            return SkillActionExecutionMode.SYNC;
        }
    }

    private static long timeoutMillis(SkillScriptAction action) {
        try {
            return Math.max(0L, action == null ? 0L : action.timeoutMillis());
        } catch (Throwable throwable) {
            return 0L;
        }
    }

    private static String safeActionId(SkillScriptAction action) {
        try {
            String id = action == null ? "unknown" : action.id();
            return Texts.isBlank(id) ? "unknown" : Texts.normalizeId(id);
        } catch (Throwable throwable) {
            return "unknown";
        }
    }

    private CompletableFuture<SkillActionResult> afterLineAsync(EntityDomain domain,
            SkillScriptContext context,
            SkillActionResult result) {
        SkillActionResult safeResult = normalize(result);
        return domain.submit(0L, () -> {
            context.refreshTargetVariables();
            return CompletableFuture.completedFuture(safeResult);
        });
    }

    private Map<String, String> resolveArguments(SkillScriptContext context, Map<String, String> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, String> resolved = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : arguments.entrySet()) {
            resolved.put(entry.getKey(), resolveText(context, entry.getValue()));
        }
        return Map.copyOf(resolved);
    }

    private String resolveText(SkillScriptContext context, String text) {
        if (Texts.isBlank(text)) {
            return text;
        }
        String resolved = Texts.formatTemplate(text, context.variables());
        if (context.plugin().coreLib().placeholderRegistry() != null) {
            ActionContext actionContext = ActionContext.create(context.plugin(), context.caster(), "skill_script", false)
                    .withPlaceholders(context.variables());
            resolved = context.plugin().coreLib().placeholderRegistry().resolve(actionContext, resolved);
        }
        return resolved;
    }

    private static SkillActionResult normalize(SkillActionResult result) {
        return result == null ? SkillActionResult.ok() : result;
    }

    private static SkillActionResult failure(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        return SkillActionResult.failure(
                SkillActionErrorType.EXECUTION_EXCEPTION,
                cause == null || Texts.isBlank(cause.getMessage())
                        ? "Skill action execution failed."
                        : cause.getMessage());
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null
                && (current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }

    private static final class EntityDomain {

        private final SkillScriptContext context;
        private final Entity caster;

        private EntityDomain(SkillScriptContext context) {
            this.context = context;
            this.caster = context == null ? null : context.caster();
        }

        private <T> CompletableFuture<T> submit(long delayTicks,
                Supplier<? extends CompletionStage<T>> task) {
            CompletableFuture<T> future = new CompletableFuture<>();
            if (context == null || context.plugin() == null || caster == null) {
                future.completeExceptionally(new IllegalStateException("Skill caster entity domain is unavailable."));
                return future;
            }
            AtomicBoolean started = new AtomicBoolean();
            Runnable invocation = () -> {
                started.set(true);
                flatten(task, future);
            };
            try {
                var scheduled = delayTicks > 0L
                        ? FoliaSchedulerAdapter.runEntityTaskLater(context.plugin(), caster, invocation, delayTicks)
                        : FoliaSchedulerAdapter.runEntityTask(context.plugin(), caster, invocation);
                if (scheduled == null) {
                    future.completeExceptionally(new IllegalStateException(
                            "Skill entity-domain task scheduling was rejected."));
                }
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
            long delayMillis = delayTicks >= (Long.MAX_VALUE - 30_000L) / 50L
                    ? Long.MAX_VALUE
                    : Math.max(30_000L, delayTicks * 50L + 30_000L);
            CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS).execute(() -> {
                if (!started.get()) {
                    future.completeExceptionally(new IllegalStateException(
                            "Skill entity-domain task did not execute before its scheduling deadline."));
                }
            });
            return future;
        }

        private <T> void flatten(Supplier<? extends CompletionStage<T>> task,
                CompletableFuture<T> future) {
            try {
                CompletionStage<T> stage = task.get();
                if (stage == null) {
                    future.completeExceptionally(new IllegalStateException(
                            "Skill entity-domain task returned no completion stage."));
                    return;
                }
                stage.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(unwrap(throwable));
                    } else {
                        future.complete(result);
                    }
                });
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        }
    }
}
