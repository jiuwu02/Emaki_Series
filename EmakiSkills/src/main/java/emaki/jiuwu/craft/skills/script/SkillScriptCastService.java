package emaki.jiuwu.craft.skills.script;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.async.AsyncFailures;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.SkillActionErrorType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.skills.model.ResolvedSkillParameters;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;

public final class SkillScriptCastService {

    private final EmakiSkillsPlugin plugin;
    private final SkillVariableResolver variableResolver;
    private final SkillScriptExecutor executor;

    public SkillScriptCastService(EmakiSkillsPlugin plugin,
            SkillVariableResolver variableResolver,
            SkillScriptExecutor executor) {
        this.plugin = plugin;
        this.variableResolver = variableResolver;
        this.executor = executor;
    }

    /**
     * Executes a skill's native script phases.
     *
     * <p>The returned result carries the failing action's error type and message so
     * callers can tell a configuration mistake apart from a runtime failure. A
     * previous revision collapsed this to a boolean, which made every script
     * problem surface as one generic "execution failed" message with no log entry.
     *
     * @param caster the casting player
     * @param definition the skill being cast
     * @param triggerId the trigger that started this cast
     * @param invocation the trigger invocation context, may be {@code null}
     * @param parameters the resolved skill parameters
     * @return the outcome of the script run, never {@code null}
     */
    public CompletableFuture<SkillActionResult> cast(Player caster,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation,
            ResolvedSkillParameters parameters) {
        if (caster == null || definition == null || !definition.script().enabled()) {
            return CompletableFuture.completedFuture(SkillActionResult.failure(
                    SkillActionErrorType.INVALID_STATE, "Skill script is not available."));
        }
        return onCaster(caster, () -> {
            Map<String, String> variables = variableResolver.resolve(
                    caster, definition, triggerId, invocation, parameters);
            SkillScriptContext context = new SkillScriptContext(
                    plugin, caster, definition, triggerId, invocation, variables);
            context.refreshTargetVariables();
            return CompletableFuture.completedFuture(new CastState(context, definition.script()));
        }).thenCompose(state -> executeMainPhases(state)
                .handle((result, throwable) -> new CastCompletion(state, result, throwable)))
                .thenCompose(completion -> {
                    if (completion.throwable() == null
                            && completion.result() != null
                            && completion.result().success()) {
                        return CompletableFuture.completedFuture(completion.result());
                    }
                    SkillActionResult failure = resolveFailure(completion);
                    // The FAIL phase is a scripted reaction to the failure, not a
                    // second chance: its own outcome must not overwrite the original
                    // cause, otherwise the reason is lost again.
                    return executor.executePhase(
                                    completion.state().context(),
                                    completion.state().script(),
                                    SkillScriptPhase.FAIL)
                            .handle((_, _) -> failure);
                });
    }

    private static SkillActionResult resolveFailure(CastCompletion completion) {
        if (completion.throwable() != null) {
            Throwable cause = AsyncFailures.unwrap(completion.throwable());
            return SkillActionResult.failure(
                    SkillActionErrorType.EXECUTION_EXCEPTION,
                    cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                            ? "Skill script execution failed."
                            : cause.getMessage());
        }
        return completion.result() == null
                ? SkillActionResult.failure(
                        SkillActionErrorType.EXECUTION_EXCEPTION, "Skill script returned no result.")
                : completion.result();
    }

    private CompletableFuture<SkillActionResult> executeMainPhases(CastState state) {
        return executor.executePhase(state.context(), state.script(), SkillScriptPhase.CAST)
                .thenCompose(result -> executor.executeHitOrMissPhase(
                        state.context(), state.script(), result));
    }

    private <T> CompletableFuture<T> onCaster(Player caster,
            Supplier<? extends CompletionStage<T>> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        AtomicBoolean started = new AtomicBoolean();
        try {
            Runnable operation = () -> {
                started.set(true);
                try {
                    CompletionStage<T> stage = task.get();
                    if (stage == null) {
                        future.completeExceptionally(new IllegalStateException(
                                "Skill cast entity-domain task returned no stage."));
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
            };
            if (plugin.threadOwnership() != null && plugin.threadOwnership().isEntityOwned(caster)) {
                operation.run();
            } else {
                var scheduled = plugin.executionDispatcher().runEntity(plugin, caster, operation,
                        () -> future.completeExceptionally(new RejectedExecutionException(
                                "Skill cast entity-domain task retired before execution.")));
                if (scheduled == null) {
                    future.completeExceptionally(new RejectedExecutionException(
                            "Skill cast entity-domain scheduling was rejected."));
                }
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        CompletableFuture.delayedExecutor(30L, TimeUnit.SECONDS).execute(() -> {
            if (!started.get()) {
                future.completeExceptionally(new IllegalStateException(
                        "Skill cast entity-domain task did not execute before its scheduling deadline."));
            }
        });
        return future;
    }

    private record CastState(SkillScriptContext context, SkillScriptDefinition script) {
    }

    private record CastCompletion(CastState state, SkillActionResult result, Throwable throwable) {
    }
}
