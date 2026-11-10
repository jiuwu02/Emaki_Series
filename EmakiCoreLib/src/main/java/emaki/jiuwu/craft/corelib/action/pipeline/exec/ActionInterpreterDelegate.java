package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.ValueParsers;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.action.pipeline.ResolvedArguments;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.ActionAst;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.PipelineLimits;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.PipelineParser;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.StaticValidator;
import emaki.jiuwu.craft.corelib.random.WeightedPool;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKeys;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import java.util.LinkedHashSet;

final class ActionInterpreterDelegate {

    private final ActionInterpreter ownerRef;

    ActionInterpreterDelegate(ActionInterpreter ownerRef) {
        this.ownerRef = ownerRef;
    }

    CompletableFuture<ActionInterpreter.State> revalidate(ActionInterpreter.Run run, ActionInterpreter.State state) {
        if (!run.owner().isEnabled()) {
            run.record("delay", PipelineOutcome.Status.SKIPPED, "action.run.owner_disabled", 0);
            return CompletableFuture.completedFuture(state.stopped("action.run.owner_disabled"));
        }
        List<CoreActionSubject> current = state.flow();
        if (current.isEmpty()) {
            return CompletableFuture.completedFuture(state);
        }
        CompletableFuture<List<CoreActionSubject>> chain = CompletableFuture.completedFuture(new ArrayList<>());
        for (CoreActionSubject subject : current) {
            chain = chain.thenCompose(survivors -> ownerRef.validateSubject(run, subject)
                    .thenApply(valid -> {
                        if (valid) {
                            survivors.add(subject);
                        }
                        return survivors;
                    }));
        }
        return chain.thenApply(survivors -> {
            if (!current.isEmpty() && survivors.isEmpty()) {
                run.record("delay", PipelineOutcome.Status.SKIPPED, "action.run.all_targets_invalid", 0);
                return state.stopped("action.run.all_targets_invalid");
            }
            return state.withFlow(survivors);
        });
    }

    static CoreActionSubject probeSubject(ActionInterpreter.State state) {
        return state.flow().isEmpty() ? state.context().caster() : state.flow().get(0);
    }

    ActionInterpreter.State actionResult(ActionInterpreter.Run run, String stageId, ActionInterpreter.State state, List<CoreActionOutcome> outcomes, int skippedTargets) {
        if (run.cancellation().cancelled()) {
            run.record(stageId, PipelineOutcome.Status.FAILURE, "action.run.cancelled", outcomes.size());
            return state.cancelled();
        }
        if (outcomes.isEmpty()) {
            String reason = skippedTargets > 0 ? "action.run.all_targets_invalid" : "action.run.no_target";
            run.record(stageId, PipelineOutcome.Status.SKIPPED, reason, 0);
            return state;
        }
        long failures = outcomes.stream().filter(CoreActionOutcome::failed).count();
        long successes = outcomes.stream().filter(CoreActionOutcome::successful).count();
        ownerRef.reporter.action(stageId, CoreStageKind.ACTION, Map.of(), (int) successes,
                (int) failures, skippedTargets,
                failures > 0L ? ActionInterpreterSupport.firstFailure(outcomes).reasonKey() : null);
        if (failures == 0L && successes == 0L) {
            run.record(stageId, PipelineOutcome.Status.SKIPPED, "action.run.all_targets_invalid", 0);
            return state.stopped("action.run.all_targets_invalid");
        }
        if (failures == 0L) {
            run.record(stageId, PipelineOutcome.Status.SUCCESS, "", outcomes.size());
            return state;
        }
        if (successes == 0L) {
            CoreActionOutcome.Failure first = ActionInterpreterSupport.firstFailure(outcomes);
            run.record(stageId, PipelineOutcome.Status.FAILURE, first.reasonKey(), outcomes.size());
            return state.failed(first.kind(), first.reasonKey(), first.args());
        }

        run.record(stageId, PipelineOutcome.Status.PARTIAL, "action.run.partial_targets", outcomes.size());
        return state.partial();
    }

    CompletableFuture<ActionInterpreter.State> weighted(ActionInterpreter.Run run, ActionAst.Weighted node, ActionInterpreter.State state, int sequenceDepth) {
        WeightedPool<List<ActionAst>> pool = new WeightedPool<>();
        for (ActionAst.Weighted.Option option : node.options()) {
            String rendered = state.context().render(option.weight());
            ExpressionEngine.NumericEvaluationResult evaluated =
                    ExpressionEngine.evaluateNumericDetailed(rendered);
            if (!evaluated.success() || !Double.isFinite(evaluated.value()) || evaluated.value() < 0D) {
                run.record(PipelineParser.WEIGHT, PipelineOutcome.Status.FAILURE,
                        "action.run.invalid_weight", state.flow().size());
                return CompletableFuture.completedFuture(state.failed(CoreActionFailureKind.INVALID_CONFIG,
                        "action.run.invalid_weight", Map.of("value", rendered)));
            }
            pool.add(option.body(), evaluated.value());
        }
        List<ActionAst> chosen = pool.roll().orElse(null);
        if (chosen == null) {
            run.record(PipelineParser.WEIGHT, PipelineOutcome.Status.SKIPPED,
                    "action.run.weight_not_rolled", state.flow().size());
            return CompletableFuture.completedFuture(state);
        }
        return ownerRef.runNested(run, chosen, state, sequenceDepth);
    }

    ActionInterpreter.State mergeSequence(ActionInterpreter.State caller, ActionInterpreter.State callee) {

        if (callee.failureKind() != null) {
            return caller.failed(callee.failureKind(), callee.reasonKey(), callee.args());
        }
        return callee.partialSeen() ? caller.partial() : caller;
    }

    PipelineOutcome summarise(ActionInterpreter.Run run, ActionInterpreter.State state) {
        List<PipelineOutcome.StageResult> stageResults = run.stageResults();
        List<CoreActionSubject> kept = run.kept();
        if (state.failureKind() != null) {
            return PipelineOutcome.failure(state.failureKind(), state.reasonKey(), state.args(),
                    stageResults, kept);
        }
        if (state.partialSeen()) {
            return PipelineOutcome.partial("action.run.partial_targets", Map.of(), stageResults, kept);
        }
        if (state.stoppedReason() != null) {
            return PipelineOutcome.skipped(state.stoppedReason(), stageResults, kept);
        }
        return PipelineOutcome.success(stageResults, kept);
    }

    ActionInterpreter.State dispatchFailure(ActionInterpreter.Run run, String stageId, ActionInterpreter.State state, Throwable throwable) {
        CoreActionOutcome.Failure failure = (CoreActionOutcome.Failure) ActionInterpreterSupport.throwableOutcome(throwable);
        run.record(stageId, PipelineOutcome.Status.FAILURE, failure.reasonKey(), state.flow().size());
        return state.failed(failure.kind(), failure.reasonKey(), failure.args());
    }

    static StageDispatcher.DispatchTarget locationTarget(PipelineContext context, CoreActionSubject subject, boolean originFirst) {
        if (originFirst && context.explicitOrigin() != null) {
            return StageDispatcher.DispatchTarget.location(context.explicitOrigin());
        }
        if (subject instanceof CoreActionSubject.OfLocation located) {
            return StageDispatcher.DispatchTarget.location(located.location());
        }
        Entity subjectEntity = subject.entityOrNull();
        if (subjectEntity != null) {
            return StageDispatcher.DispatchTarget.entity(subjectEntity);
        }
        if (context.explicitOrigin() != null) {
            return StageDispatcher.DispatchTarget.location(context.explicitOrigin());
        }
        Entity caster = context.caster().entityOrNull();
        return caster == null
                ? StageDispatcher.DispatchTarget.location(null)
                : StageDispatcher.DispatchTarget.entity(caster);
    }

}
