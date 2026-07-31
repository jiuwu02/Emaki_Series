package emaki.jiuwu.craft.corelib.action.v2.exec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.v2.PipelineContext;
import emaki.jiuwu.craft.corelib.action.v2.ResolvedArguments;
import emaki.jiuwu.craft.corelib.action.v2.compile.ActionAst;
import emaki.jiuwu.craft.corelib.action.v2.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.action.v2.compile.PipelineLimits;
import emaki.jiuwu.craft.corelib.action.v2.compile.StaticValidator;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionKeys;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Runs a {@link CompiledPipeline}.
 *
 * <p>Two properties matter most. Nothing is parsed here: the hot path walks an AST that was validated
 * at load time. And every stage is dispatched through {@link StageDispatcher} with the domain the
 * stage declared, so no domain is ever inferred at runtime.</p>
 */
public final class ActionInterpreter {

    private final StageInvoker invoker;
    private final StageDispatcher dispatcher;
    private final SequenceRepository sequences;
    private final PipelineLimits limits;

    /**
     * Creates an interpreter.
     *
     * @param invoker stage execution seam
     * @param dispatcher the single scheduling entry point
     * @param sequences named sequences available to {@code run}
     * @param limits compile limits, reused at runtime for sequence depth
     */
    public ActionInterpreter(@NotNull StageInvoker invoker,
            @NotNull StageDispatcher dispatcher,
            @Nullable SequenceRepository sequences,
            @Nullable PipelineLimits limits) {
        this.invoker = java.util.Objects.requireNonNull(invoker, "invoker");
        this.dispatcher = java.util.Objects.requireNonNull(dispatcher, "dispatcher");
        this.sequences = sequences == null ? SequenceRepository.empty() : sequences;
        this.limits = limits == null ? PipelineLimits.defaults() : limits;
    }

    /**
     * Runs a pipeline.
     *
     * @param owner plugin owning this invocation
     * @param pipeline the compiled pipeline
     * @param context root context
     * @return the pipeline outcome
     */
    public @NotNull CompletableFuture<PipelineOutcome> run(@NotNull Plugin owner,
            @NotNull CompiledPipeline pipeline,
            @NotNull PipelineContext context) {
        java.util.Objects.requireNonNull(owner, "owner");
        java.util.Objects.requireNonNull(pipeline, "pipeline");
        java.util.Objects.requireNonNull(context, "context");
        if (pipeline.empty()) {
            return CompletableFuture.completedFuture(
                    PipelineOutcome.skipped("action.v2.run.empty_pipeline", List.of()));
        }
        CancellationSignal cancellation = new CancellationSignal();
        PipelineContext rooted = context.with(CoreActionKeys.CANCELLATION, cancellation);
        Run run = new Run(owner, cancellation, new ArrayList<>());
        return execute(run, pipeline.nodes(), rooted, pipeline.implicitSelfSource(), 0)
                .thenApply(state -> summarise(run, state));
    }

    private CompletableFuture<State> execute(Run run,
            List<ActionAst> nodes,
            PipelineContext context,
            boolean implicitSelf,
            int sequenceDepth) {
        CompletableFuture<State> chain = implicitSelf
                ? applySource(run, StaticValidator.SELF_SOURCE, Map.of(), State.start(context))
                : CompletableFuture.completedFuture(State.start(context));
        for (ActionAst node : nodes) {
            chain = chain.thenCompose(state -> state.terminal()
                    ? CompletableFuture.completedFuture(state)
                    : step(run, node, state, sequenceDepth));
        }
        return chain;
    }

    private CompletableFuture<State> step(Run run, ActionAst node, State state, int sequenceDepth) {
        if (run.cancellation().cancelled()) {
            return CompletableFuture.completedFuture(state.cancelled());
        }
        return switch (node) {
            case ActionAst.Branch branch -> branch(run, branch, state, sequenceDepth);
            case ActionAst.SequenceCall call -> sequence(run, call, state, sequenceDepth);
            case ActionAst.Stage stage -> stage(run, stage, state);
        };
    }

    private CompletableFuture<State> stage(Run run, ActionAst.Stage node, State state) {
        StageInvoker.Handle handle = invoker.resolve(node.id());
        if (handle == null) {
            run.record(node.id(), PipelineOutcome.Status.FAILURE, "action.v2.run.stage_unavailable", 0);
            return CompletableFuture.completedFuture(state.failed(CoreActionFailureKind.OWNER_DISABLED,
                    "action.v2.run.stage_unavailable", Map.of("stage", node.id())));
        }
        return switch (handle.kind()) {
            case SOURCE -> applySource(run, node.id(), node.arguments(), state);
            case GATE -> applyGate(run, handle, node.arguments(), state);
            case ACTION -> applyAction(run, handle, node.arguments(), state);
        };
    }

    private CompletableFuture<State> applySource(Run run,
            String stageId,
            Map<String, String> raw,
            State state) {
        StageInvoker.Handle handle = invoker.resolve(stageId);
        if (handle == null || handle.kind() != CoreStageKind.SOURCE) {
            run.record(stageId, PipelineOutcome.Status.FAILURE, "action.v2.run.stage_unavailable", 0);
            return CompletableFuture.completedFuture(state.failed(CoreActionFailureKind.OWNER_DISABLED,
                    "action.v2.run.stage_unavailable", Map.of("stage", Texts.toStringSafe(stageId))));
        }
        PipelineContext context = state.context();
        StageDispatcher.DispatchTarget target = dispatchTarget(
                invoker.domainOf(handle, context, context.caster(), raw), context, context.caster());
        return dispatcher.dispatch(run.owner(), target, 0L, handle.id(), handle.timeoutMillis(),
                run.cancellation(),
                () -> invoker.invokeSource(handle, context, arguments(handle, render(context, raw))))
                .handle((result, throwable) -> throwable != null
                        ? dispatchFailure(run, handle.id(), state, throwable)
                        : sourceResult(run, handle.id(), state, result));
    }

    private State sourceResult(Run run, String stageId, State state, CoreSourceResult result) {
        return switch (result) {
            case CoreSourceResult.Selected selected -> {
                run.record(stageId, PipelineOutcome.Status.SUCCESS, "", selected.subjects().size());
                yield state.withFlow(selected.subjects());
            }
            case CoreSourceResult.Empty empty -> {
                run.record(stageId, PipelineOutcome.Status.SKIPPED, empty.reasonKey(), 0);
                yield state.stopped(empty.reasonKey());
            }
            case CoreSourceResult.Invalid invalid -> {
                run.record(stageId, PipelineOutcome.Status.FAILURE, invalid.reasonKey(), 0);
                yield state.failed(CoreActionFailureKind.INVALID_CONFIG, invalid.reasonKey(), invalid.args());
            }
        };
    }

    private CompletableFuture<State> applyGate(Run run,
            StageInvoker.Handle handle,
            Map<String, String> raw,
            State state) {
        PipelineContext context = state.context();
        List<CoreActionSubject> inbound = state.flow();
        CoreActionSubject probe = inbound.isEmpty() ? context.caster() : inbound.get(0);
        StageDispatcher.DispatchTarget target = dispatchTarget(
                invoker.domainOf(handle, context, probe, raw), context, probe);
        return dispatcher.dispatch(run.owner(), target, 0L, handle.id(), handle.timeoutMillis(),
                run.cancellation(),
                () -> invoker.invokeGate(handle, context, inbound, arguments(handle, render(context, raw))))
                .handle((result, throwable) -> throwable != null
                        ? dispatchFailure(run, handle.id(), state, throwable)
                        : gateResult(run, handle.id(), state, result));
    }

    private State gateResult(Run run, String stageId, State state, CoreGateResult result) {
        return switch (result) {
            case CoreGateResult.Passed passed -> {
                run.record(stageId, PipelineOutcome.Status.SUCCESS, "", passed.outbound().size());
                PipelineContext derived = state.context()
                        .withVariables(passed.variables())
                        .withData(passed.data());
                yield state.withContext(derived).withFlow(passed.outbound());
            }
            case CoreGateResult.Halted halted -> {
                run.record(stageId, PipelineOutcome.Status.SKIPPED, halted.reasonKey(), state.flow().size());
                yield state.stopped(halted.reasonKey());
            }
            case CoreGateResult.Invalid invalid -> {
                run.record(stageId, PipelineOutcome.Status.FAILURE, invalid.reasonKey(), state.flow().size());
                yield state.failed(CoreActionFailureKind.INVALID_CONFIG, invalid.reasonKey(), invalid.args());
            }
        };
    }

    private CompletableFuture<State> applyAction(Run run,
            StageInvoker.Handle handle,
            Map<String, String> raw,
            State state) {
        PipelineContext context = state.context();
        if (handle.targetRequirement() == CoreTargetRequirement.NONE) {
            return invokeOnce(run, handle, raw, state, context.withTargets(List.of()), -1)
                    .thenApply(outcome -> actionResult(run, handle.id(), state, List.of(outcome), 0));
        }

        // Snapshot the flow now. A stage that removes an entity must not shorten the iteration of the
        // stage it shares a flow with; that is what "iteration order is stable" means for a pipeline.
        List<CoreActionSubject> flow = state.flow();
        if (flow.isEmpty()) {
            if (handle.targetRequirement().requiresTarget()) {
                run.record(handle.id(), PipelineOutcome.Status.SKIPPED, "action.v2.run.no_target", 0);
                return CompletableFuture.completedFuture(state.stopped("action.v2.run.no_target"));
            }
            return invokeOnce(run, handle, raw, state, context, -1)
                    .thenApply(outcome -> actionResult(run, handle.id(), state, List.of(outcome), 0));
        }

        PipelineContext iterationContext = context.withTargets(flow);
        List<CoreActionOutcome> outcomes = new ArrayList<>();
        int[] skippedTargets = {0};
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (int index = 0; index < flow.size(); index++) {
            int position = index;
            chain = chain.thenCompose(ignored -> {
                if (run.cancellation().cancelled()) {
                    return CompletableFuture.completedFuture(null);
                }
                CoreActionSubject subject = flow.get(position);
                if (!subject.valid()) {
                    // Revalidated per target rather than once up front: on Folia a target can be
                    // removed while an earlier target in the same flow is still being processed.
                    skippedTargets[0]++;
                    return CompletableFuture.completedFuture(null);
                }
                if (!handle.targetRequirement().accepts(subject)) {
                    skippedTargets[0]++;
                    return CompletableFuture.completedFuture(null);
                }
                return invokeOnce(run, handle, raw, state, iterationContext, position)
                        .thenAccept(outcomes::add);
            });
        }
        return chain.thenApply(ignored ->
                actionResult(run, handle.id(), state, List.copyOf(outcomes), skippedTargets[0]));
    }

    private CompletableFuture<CoreActionOutcome> invokeOnce(Run run,
            StageInvoker.Handle handle,
            Map<String, String> raw,
            State state,
            PipelineContext iterationContext,
            int targetIndex) {
        PipelineContext invocationContext = targetIndex < 0
                ? iterationContext
                : iterationContext.withTargetIndex(targetIndex);
        CoreActionSubject subject = invocationContext.currentTarget();
        StageDispatcher.DispatchTarget target = dispatchTarget(
                invoker.domainOf(handle, invocationContext, subject, raw), invocationContext, subject);
        // Placeholders are rendered inside the dispatched task, not before it: %target.health% must be
        // read on the thread that owns the target, and per-target rendering is why each iteration gets
        // its own arguments.
        return dispatcher.dispatch(run.owner(), target, 0L, handle.id(), handle.timeoutMillis(),
                run.cancellation(),
                () -> invoker.invokeAction(handle, invocationContext,
                        arguments(handle, render(invocationContext, raw))))
                .handle((outcome, throwable) -> throwable == null
                        ? outcome
                        : throwableOutcome(throwable));
    }

    private State actionResult(Run run,
            String stageId,
            State state,
            List<CoreActionOutcome> outcomes,
            int skippedTargets) {
        if (run.cancellation().cancelled()) {
            run.record(stageId, PipelineOutcome.Status.FAILURE, "action.v2.run.cancelled", outcomes.size());
            return state.cancelled();
        }
        if (outcomes.isEmpty()) {
            String reason = skippedTargets > 0 ? "action.v2.run.all_targets_invalid" : "action.v2.run.no_target";
            run.record(stageId, PipelineOutcome.Status.SKIPPED, reason, 0);
            return state;
        }
        long failures = outcomes.stream().filter(CoreActionOutcome::failed).count();
        long successes = outcomes.stream().filter(CoreActionOutcome::successful).count();
        if (failures == 0L) {
            run.record(stageId, PipelineOutcome.Status.SUCCESS, "", outcomes.size());
            return state;
        }
        if (successes == 0L) {
            CoreActionOutcome.Failure first = firstFailure(outcomes);
            run.record(stageId, PipelineOutcome.Status.FAILURE, first.reasonKey(), outcomes.size());
            return state.failed(first.kind(), first.reasonKey(), first.args());
        }
        // Partial is recorded but does not stop the pipeline: "three of five targets were immune" is a
        // gameplay result, not a configuration error, and later stages still have work to do.
        run.record(stageId, PipelineOutcome.Status.PARTIAL, "action.v2.run.partial_targets", outcomes.size());
        return state.partial();
    }

    private CompletableFuture<State> branch(Run run,
            ActionAst.Branch node,
            State state,
            int sequenceDepth) {
        String condition = state.context().render(node.condition());
        Boolean evaluated = ExpressionEngine.evaluateBoolean(condition);
        if (evaluated == null) {
            run.record("if", PipelineOutcome.Status.FAILURE, "action.v2.run.invalid_condition", state.flow().size());
            return CompletableFuture.completedFuture(state.failed(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.run.invalid_condition", Map.of("condition", condition)));
        }
        List<ActionAst> chosen = evaluated ? node.thenBranch() : node.elseBranch();
        if (chosen.isEmpty()) {
            return CompletableFuture.completedFuture(state);
        }
        return runNested(run, chosen, state, sequenceDepth);
    }

    private CompletableFuture<State> sequence(Run run,
            ActionAst.SequenceCall node,
            State state,
            int sequenceDepth) {
        if (sequenceDepth >= limits.maxSequenceDepth()) {
            run.record("run", PipelineOutcome.Status.FAILURE, "action.v2.run.sequence_depth_exceeded", 0);
            return CompletableFuture.completedFuture(state.failed(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.run.sequence_depth_exceeded",
                    Map.of("sequence", node.sequence(), "maximum", limits.maxSequenceDepth())));
        }
        CompiledPipeline target = sequences.find(node.sequence());
        if (target == null) {
            run.record("run", PipelineOutcome.Status.FAILURE, "action.v2.run.unknown_sequence", 0);
            return CompletableFuture.completedFuture(state.failed(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.run.unknown_sequence", Map.of("sequence", node.sequence())));
        }
        PipelineContext isolated = state.context()
                .withTargets(state.flow())
                .isolated(render(state.context(), node.parameters()));
        return execute(run, target.nodes(), isolated, target.implicitSelfSource(), sequenceDepth + 1)
                .thenApply(inner -> mergeSequence(state, inner));
    }

    private CompletableFuture<State> runNested(Run run,
            List<ActionAst> nodes,
            State state,
            int sequenceDepth) {
        CompletableFuture<State> chain = CompletableFuture.completedFuture(state);
        for (ActionAst node : nodes) {
            chain = chain.thenCompose(current -> current.terminal()
                    ? CompletableFuture.completedFuture(current)
                    : step(run, node, current, sequenceDepth));
        }
        return chain;
    }

    private State mergeSequence(State caller, State callee) {
        // A sequence call is isolated in variables but not in failure: if the callee failed, the caller
        // must not continue as if nothing happened.
        if (callee.failureKind() != null) {
            return caller.failed(callee.failureKind(), callee.reasonKey(), callee.args());
        }
        return callee.partialSeen() ? caller.partial() : caller;
    }

    private PipelineOutcome summarise(Run run, State state) {
        List<PipelineOutcome.StageResult> stageResults = run.stageResults();
        if (state.failureKind() != null) {
            return PipelineOutcome.failure(state.failureKind(), state.reasonKey(), state.args(), stageResults);
        }
        if (state.partialSeen()) {
            return PipelineOutcome.partial("action.v2.run.partial_targets", Map.of(), stageResults);
        }
        if (state.stoppedReason() != null) {
            return PipelineOutcome.skipped(state.stoppedReason(), stageResults);
        }
        return PipelineOutcome.success(stageResults);
    }

    private State dispatchFailure(Run run, String stageId, State state, Throwable throwable) {
        CoreActionOutcome.Failure failure = (CoreActionOutcome.Failure) throwableOutcome(throwable);
        run.record(stageId, PipelineOutcome.Status.FAILURE, failure.reasonKey(), state.flow().size());
        return state.failed(failure.kind(), failure.reasonKey(), failure.args());
    }

    private static CoreActionOutcome throwableOutcome(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) {
            cause = cause.getCause();
        }
        if (cause instanceof java.util.concurrent.TimeoutException) {
            return CoreActionOutcome.failure(CoreActionFailureKind.TIMEOUT, "action.v2.run.timeout");
        }
        if (cause instanceof java.util.concurrent.CancellationException) {
            return CoreActionOutcome.failure(CoreActionFailureKind.OWNER_DISABLED, "action.v2.run.cancelled");
        }
        if (cause instanceof StageDispatcher.OwnerDisabledException) {
            return CoreActionOutcome.failure(CoreActionFailureKind.OWNER_DISABLED,
                    "action.v2.run.owner_disabled", Map.of("error", Texts.toStringSafe(cause.getMessage())));
        }
        if (cause instanceof StageDispatcher.StageRetiredException) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.v2.run.target_retired", Map.of("error", Texts.toStringSafe(cause.getMessage())));
        }
        return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR, "action.v2.run.exception",
                Map.of("error", cause == null ? "" : Texts.toStringSafe(cause.getMessage())));
    }

    private static CoreActionOutcome.Failure firstFailure(List<CoreActionOutcome> outcomes) {
        for (CoreActionOutcome outcome : outcomes) {
            if (outcome instanceof CoreActionOutcome.Failure failure) {
                return failure;
            }
        }
        return new CoreActionOutcome.Failure(CoreActionFailureKind.INTERNAL_ERROR,
                "action.v2.run.exception", Map.of());
    }

    private static ResolvedArguments arguments(StageInvoker.Handle handle, Map<String, String> rendered) {
        return ResolvedArguments.of(rendered, handle.parameters());
    }

    private static Map<String, String> render(PipelineContext context, Map<String, String> raw) {
        if (raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> rendered = new LinkedHashMap<>();
        raw.forEach((key, value) -> rendered.put(key, context.render(value)));
        return Map.copyOf(rendered);
    }

    private static StageDispatcher.DispatchTarget dispatchTarget(ExecutionDomain domain,
            PipelineContext context,
            CoreActionSubject subject) {
        return switch (domain) {
            case SERVER_GLOBAL -> StageDispatcher.DispatchTarget.global();
            case ASYNC_COMPUTE -> StageDispatcher.DispatchTarget.async();
            case PHYSICAL_FILE -> StageDispatcher.DispatchTarget.physicalFile();
            case ENTITY -> StageDispatcher.DispatchTarget.entity(subject.entityOrNull() != null
                    ? subject.entityOrNull()
                    : context.caster().entityOrNull());
            case LOCATION_REGION -> StageDispatcher.DispatchTarget.location(subject.location() != null
                    ? subject.location()
                    : (context.hasOrigin() ? context.origin() : null));
        };
    }

    /** One invocation's mutable bookkeeping: owner, cancellation and the per-stage log. */
    private record Run(Plugin owner,
            CancellationSignal cancellation,
            List<PipelineOutcome.StageResult> stageResults) {

        private void record(String stageId, PipelineOutcome.Status status, String reasonKey, int targetCount) {
            stageResults.add(new PipelineOutcome.StageResult(stageId, status, reasonKey, targetCount));
        }
    }

    /**
     * Immutable interpreter state threaded through the pipeline.
     *
     * <p>Deliberately a value rather than a mutable cursor: a stage cannot reach back and change what
     * an earlier stage decided, which is the property the v1 {@code sharedState} map lacked.</p>
     */
    private record State(PipelineContext context,
            List<CoreActionSubject> flow,
            @Nullable String stoppedReason,
            @Nullable CoreActionFailureKind failureKind,
            String reasonKey,
            Map<String, Object> args,
            boolean partialSeen) {

        private static State start(PipelineContext context) {
            return new State(context, context.targets(), null, null, "", Map.of(), false);
        }

        private boolean terminal() {
            return stoppedReason != null || failureKind != null;
        }

        private State withContext(PipelineContext newContext) {
            return new State(newContext, flow, stoppedReason, failureKind, reasonKey, args, partialSeen);
        }

        private State withFlow(List<CoreActionSubject> newFlow) {
            List<CoreActionSubject> copy = newFlow == null ? List.of() : List.copyOf(newFlow);
            return new State(context.withTargets(copy), copy, stoppedReason, failureKind,
                    reasonKey, args, partialSeen);
        }

        private State stopped(String reason) {
            return new State(context, flow, reason == null ? "" : reason, failureKind,
                    reasonKey, args, partialSeen);
        }

        private State failed(CoreActionFailureKind kind, String reason, Map<String, Object> failureArgs) {
            return new State(context, flow, stoppedReason,
                    kind == null ? CoreActionFailureKind.INTERNAL_ERROR : kind,
                    reason == null ? "" : reason,
                    failureArgs == null ? Map.of() : Map.copyOf(failureArgs),
                    partialSeen);
        }

        private State cancelled() {
            return failed(CoreActionFailureKind.OWNER_DISABLED, "action.v2.run.cancelled", Map.of());
        }

        private State partial() {
            return new State(context, flow, stoppedReason, failureKind, reasonKey, args, true);
        }
    }
}
