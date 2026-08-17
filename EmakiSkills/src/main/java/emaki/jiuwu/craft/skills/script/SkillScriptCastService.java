package emaki.jiuwu.craft.skills.script;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.action.pipeline.exec.PipelineOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;

import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.corelib.trigger.TriggerInvocation;

public final class SkillScriptCastService {

    private final EmakiSkillsPlugin plugin;
    private final SkillPipelineRuntime runtime;

    public SkillScriptCastService(EmakiSkillsPlugin plugin,
            SkillPipelineRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    public CompletableFuture<PipelineOutcome> cast(Player caster,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation,
            Map<String, String> variables) {
        if (caster == null || definition == null || !definition.script().enabled()) {
            return CompletableFuture.completedFuture(PipelineOutcome.failure(
                    CoreActionFailureKind.REJECTED, "skill.script_unavailable", Map.of(), List.of()));
        }

        return onCaster(caster, () -> CompletableFuture.completedFuture(
                        newSession(caster, definition, triggerId, invocation, variables)))
                .thenCompose(session -> executeMainPhases(session)
                        .handle((outcome, throwable) -> new Completion(session, outcome, throwable)))
                .thenCompose(this::finish);
    }

    private CastSession newSession(Player caster,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation,
            Map<String, String> variables) {
        return new CastSession(caster, definition, variables, triggerTargets(invocation),
                invocation == null ? null : invocation.targetLocation());
    }

    private static List<CoreActionSubject> triggerTargets(TriggerInvocation invocation) {
        if (invocation == null) {
            return List.of();
        }
        if (invocation.targetEntity() != null) {
            return List.of(CoreActionSubject.of(invocation.targetEntity()));
        }
        Location location = invocation.targetLocation();
        return location == null ? List.of() : List.of(CoreActionSubject.of(location));
    }

    private CompletableFuture<PipelineOutcome> executeMainPhases(CastSession session) {
        return runPhase(session, SkillScriptPhase.CAST)
                .thenCompose(castOutcome -> {
                    if (castOutcome.status() == PipelineOutcome.Status.FAILURE
                            && session.script().stopOnFailure()) {
                        return CompletableFuture.completedFuture(castOutcome);
                    }

                    return runPhase(session, session.hasTargets()
                            ? SkillScriptPhase.HIT
                            : SkillScriptPhase.MISS);
                });
    }

    private CompletableFuture<PipelineOutcome> finish(Completion completion) {
        if (completion.throwable() == null
                && completion.outcome() != null
                && completion.outcome().status() != PipelineOutcome.Status.FAILURE) {
            return CompletableFuture.completedFuture(completion.outcome());
        }
        PipelineOutcome failure = resolveFailure(completion);

        return runPhase(completion.session(), SkillScriptPhase.FAIL).handle((_, _) -> failure);
    }

    private static PipelineOutcome resolveFailure(Completion completion) {
        if (completion.throwable() != null) {
            Throwable cause = AsyncFailures.unwrap(completion.throwable());
            return PipelineOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.run.exception",
                    Map.of("error", cause == null || cause.getMessage() == null
                            ? "Skill script execution failed."
                            : cause.getMessage()),
                    List.of());
        }
        return completion.outcome() == null
                ? PipelineOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                        "action.run.exception", Map.of("error", "no result"), List.of())
                : completion.outcome();
    }

    private CompletableFuture<PipelineOutcome> runPhase(CastSession session, SkillScriptPhase phase) {
        PipelineContext context = session.context(phase);
        List<String> conditions = session.script().conditions(phase);
        if (!conditions.isEmpty() && !ConditionEvaluator.evaluate(conditions, "all_of", null,
                text -> runtime.placeholders().render(context, text), true)) {
            return CompletableFuture.completedFuture(
                    PipelineOutcome.skipped("skill.script_phase_condition_failed", List.of()));
        }
        return runtime.runPhase(session.definition().id(), session.script(), phase, context)
                .thenApply(outcome -> {
                    session.absorb(outcome);
                    return outcome;
                });
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
            if (plugin.scheduling().ownsEntity(caster)) {
                operation.run();
            } else {
                plugin.scheduling().runForEntity(plugin, caster, operation,
                        () -> future.completeExceptionally(new RejectedExecutionException(
                                "Skill cast entity-domain task retired before execution.")));
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

    private final class CastSession {

        private final Player caster;
        private final SkillDefinition definition;
        private final Map<String, String> variables;
        private List<CoreActionSubject> targets;
        private Location origin;

        private CastSession(Player caster,
                SkillDefinition definition,
                Map<String, String> variables,
                List<CoreActionSubject> targets,
                Location origin) {
            this.caster = caster;
            this.definition = definition;
            this.variables = variables == null ? Map.of() : Map.copyOf(variables);
            this.targets = targets == null ? List.of() : List.copyOf(targets);
            this.origin = origin;
        }

        private SkillDefinition definition() {
            return definition;
        }

        private SkillScriptDefinition script() {
            return definition.script();
        }

        private boolean hasTargets() {
            return !targets.isEmpty();
        }

        private void absorb(PipelineOutcome outcome) {
            if (outcome == null || outcome.keptFlow().isEmpty()) {
                return;
            }
            targets = List.copyOf(outcome.keptFlow());
            origin = targets.get(0).location();
        }

        private PipelineContext context(SkillScriptPhase phase) {
            return PipelineContext.root(plugin, CoreActionSubject.of(caster), origin,
                            phase.configKey(), false, runtime.placeholders())
                    .withVariables(variables)
                    .withTargets(targets);
        }
    }

    private record Completion(CastSession session, PipelineOutcome outcome, Throwable throwable) {
    }
}
