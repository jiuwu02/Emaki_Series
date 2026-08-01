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
import emaki.jiuwu.craft.corelib.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.model.ResolvedSkillParameters;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;

/**
 * Runs a skill's script phases as pipelines.
 *
 * <p>Phase ordering is unchanged: {@code cast}, then {@code hit} or {@code miss}, then {@code fail} when
 * something went wrong. What changed is how targets cross a phase boundary. v1 relied on an action writing
 * back into a mutable context ({@code ray save=target}); pipeline contexts are read-only, so the only channel
 * is the explicit {@code keep} gate, whose flow CoreLib reports as {@link PipelineOutcome#keptFlow()}.</p>
 */
public final class SkillScriptCastService {

    private final EmakiSkillsPlugin plugin;
    private final SkillVariableResolver variableResolver;
    private final SkillPipelineRuntime runtime;

    public SkillScriptCastService(EmakiSkillsPlugin plugin,
            SkillVariableResolver variableResolver,
            SkillPipelineRuntime runtime) {
        this.plugin = plugin;
        this.variableResolver = variableResolver;
        this.runtime = runtime;
    }

    /**
     * Executes a skill's native script phases.
     *
     * <p>The returned outcome carries the failure classification and reason key of the failing line, so callers
     * can tell a configuration mistake apart from a runtime failure.</p>
     *
     * @param caster the casting player
     * @param definition the skill being cast
     * @param triggerId the trigger that started this cast
     * @param invocation the trigger invocation context, may be {@code null}
     * @param parameters the resolved skill parameters
     * @return the outcome of the script run, never {@code null}
     */
    public CompletableFuture<PipelineOutcome> cast(Player caster,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation,
            ResolvedSkillParameters parameters) {
        if (caster == null || definition == null || !definition.script().enabled()) {
            return CompletableFuture.completedFuture(PipelineOutcome.failure(
                    CoreActionFailureKind.REJECTED, "skill.script_unavailable", Map.of(), List.of()));
        }
        // Still hops to the caster's thread first: building the root context reads the caster's location, and
        // the variable resolver reads live player state. Everything inside a phase is dispatched by CoreLib.
        return onCaster(caster, () -> CompletableFuture.completedFuture(
                        newSession(caster, definition, triggerId, invocation, parameters)))
                .thenCompose(session -> executeMainPhases(session)
                        .handle((outcome, throwable) -> new Completion(session, outcome, throwable)))
                .thenCompose(this::finish);
    }

    private CastSession newSession(Player caster,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation,
            ResolvedSkillParameters parameters) {
        Map<String, String> variables = variableResolver.resolve(
                caster, definition, triggerId, invocation, parameters);
        return new CastSession(caster, definition, variables, triggerTargets(invocation),
                invocation == null ? null : invocation.targetLocation());
    }

    /**
     * The targets the trigger supplied.
     *
     * <p>A passive trigger names what the skill reacted to: {@code combo_attack} passes the entity that was
     * just hit, not whatever the caster happens to be looking at. Reading it here is what lets an
     * {@code inherited} source in the {@code cast} phase see it.</p>
     */
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
                    // HIT/MISS reads only the session flow, never the pipeline's final flow. The final flow
                    // depends on which line came last, so a `send_message` after `keep` would make its implicit
                    // `self` source look like a hit.
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
        // The FAIL phase is a scripted reaction to the failure, not a second chance: its own outcome must not
        // overwrite the original cause, otherwise the reason is lost again.
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

    /**
     * Runs one phase and folds its kept flow back into the session.
     *
     * <p>The phase-level {@code conditions:} list is still evaluated here rather than by the pipeline: it
     * guards the whole phase, which has no per-line equivalent.</p>
     */
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

    /**
     * One cast's cross-phase state: the target flow and origin every phase starts from.
     *
     * <p>Starts at whatever the trigger supplied, so a passive skill that defines a {@code hit} phase reaches
     * it the same way v1's {@code targetPresent} did. Only a {@code keep} gate replaces it, which is why the
     * handoff no longer depends on where a line sits within its phase.</p>
     */
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

        /**
         * Takes over the flow a {@code keep} gate recorded.
         *
         * <p>An outcome with no kept flow leaves the session untouched: a phase without {@code keep} is a phase
         * that did not ask to change what the next one sees.</p>
         */
        private void absorb(PipelineOutcome outcome) {
            if (outcome == null || outcome.keptFlow().isEmpty()) {
                return;
            }
            targets = List.copyOf(outcome.keptFlow());
            origin = targets.get(0).location();
        }

        /**
         * Builds the root context for one phase.
         *
         * <p>{@code origin} is the session's target position rather than the caster's, because {@code nearby}
         * centres on {@code origin} and no stage can move it. That is what makes "area damage around what this
         * skill hit" expressible.</p>
         */
        private PipelineContext context(SkillScriptPhase phase) {
            return PipelineContext.root(plugin, CoreActionSubject.of(caster), origin,
                            phase.configKey(), false, runtime.placeholders())
                    .withVariables(variables)
                    .withTargets(targets);
        }
    }

    /**
     * Carries a phase run's outcome together with any scheduling failure.
     *
     * @param session the cast this belongs to
     * @param outcome the pipeline outcome, {@code null} when the run threw
     * @param throwable the scheduling or execution failure, {@code null} on success
     */
    private record Completion(CastSession session, PipelineOutcome outcome, Throwable throwable) {
    }
}
