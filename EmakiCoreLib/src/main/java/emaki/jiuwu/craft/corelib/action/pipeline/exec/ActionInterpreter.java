package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.ValueParsers;
import emaki.jiuwu.craft.corelib.action.pipeline.PipelineContext;
import emaki.jiuwu.craft.corelib.action.pipeline.ResolvedArguments;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.ActionAst;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompiledPipeline;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.PipelineLimits;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.StaticValidator;
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
                    PipelineOutcome.skipped("action.run.empty_pipeline", List.of()));
        }
        CancellationSignal cancellation = new CancellationSignal();
        PipelineContext rooted = context.with(CoreActionKeys.CANCELLATION, cancellation);
        Run run = Run.start(owner, cancellation);
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
        return walk(run, chain, nodes, sequenceDepth);
    }

    /**
     * Walks a node list, batching stages and handing control nodes their own step.
     *
     * <p>A timing stage takes everything after it as its body, because {@code after 10t | self | msg}
     * means "run {@code self | msg} 10 ticks later" rather than "transform the flow". The walk
     * therefore stops at the first timing stage and nests the remainder.</p>
     */
    private CompletableFuture<State> walk(Run run,
            CompletableFuture<State> chain,
            List<ActionAst> nodes,
            int sequenceDepth) {
        CompletableFuture<State> current = chain;
        // Consecutive plain stages are collected into runs so same-domain stages can share one
        // dispatch. Branches and sequence calls break a run because their bodies are separate
        // pipelines whose domains are only known once their own stages resolve.
        List<ActionAst> pending = new ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            ActionAst node = nodes.get(index);
            if (node instanceof ActionAst.Stage stage && StaticValidator.timingStage(stage.id())) {
                current = flush(run, current, pending);
                List<ActionAst> body = List.copyOf(nodes.subList(index + 1, nodes.size()));
                return current.thenCompose(state -> state.terminal()
                        ? CompletableFuture.completedFuture(state)
                        : timed(run, stage, body, state, sequenceDepth));
            }
            if (node instanceof ActionAst.Stage) {
                pending.add(node);
                continue;
            }
            current = flush(run, current, pending);
            pending = new ArrayList<>();
            ActionAst control = node;
            current = current.thenCompose(state -> state.terminal()
                    ? CompletableFuture.completedFuture(state)
                    : step(run, control, state, sequenceDepth));
        }
        return flush(run, current, pending);
    }

    private CompletableFuture<State> flush(Run run,
            CompletableFuture<State> chain,
            List<ActionAst> pending) {
        if (pending.isEmpty()) {
            return chain;
        }
        List<ActionAst> batch = List.copyOf(pending);
        return chain.thenCompose(state -> state.terminal()
                ? CompletableFuture.completedFuture(state)
                : runStageRun(run, batch, state));
    }

    private CompletableFuture<State> timed(Run run,
            ActionAst.Stage stage,
            List<ActionAst> body,
            State state,
            int sequenceDepth) {
        StageInvoker.Handle handle = invoker.resolve(stage.id());
        if (handle == null) {
            run.record(stage.id(), PipelineOutcome.Status.FAILURE, "action.run.stage_unavailable", 0);
            return CompletableFuture.completedFuture(state.failed(CoreActionFailureKind.OWNER_DISABLED,
                    "action.run.stage_unavailable", Map.of("stage", stage.id())));
        }
        ResolvedArguments arguments = arguments(handle, render(state.context(), stage.arguments()));
        boolean isAfter = StaticValidator.AFTER_STAGE.equals(stage.id());
        long intervalTicks = isAfter
                ? arguments.getDurationTicks("delay", 0L)
                : arguments.getDurationTicks("interval", 0L);
        if (intervalTicks <= 0L && !stage.positional().isEmpty()) {
            // The compiler names positional values before execution, but the interpreter must not depend
            // on that having happened: reading the bare value keeps a hand-built AST behaving the same.
            intervalTicks = Math.max(0L, ValueParsers.parseTicks(
                    state.context().render(stage.positional().get(0))));
        }
        if (body.isEmpty()) {
            run.record(stage.id(), PipelineOutcome.Status.SKIPPED, "action.run.timing_without_body", 0);
            return CompletableFuture.completedFuture(state.stopped("action.run.timing_without_body"));
        }
        // times counts extra runs on top of the first one (decision D4 makes 0 the default), so a plain
        // `after` and `every ... times 0` both run the body exactly once.
        int extraRuns = 0;
        if (!isAfter) {
            extraRuns = Math.max(0, arguments.getInt("times", 0));
            if (extraRuns == 0) {
                // Written form is `every <interval> times <count>`, so the count follows the literal
                // `times` keyword rather than sitting at a fixed index.
                List<String> positional = stage.positional();
                for (int index = 0; index + 1 < positional.size(); index++) {
                    if ("times".equalsIgnoreCase(positional.get(index))) {
                        extraRuns = Math.max(0, ValueParsers.parseInt(
                                state.context().render(positional.get(index + 1)), 0));
                        break;
                    }
                }
            }
        }
        run.record(stage.id(), PipelineOutcome.Status.SUCCESS, "", state.flow().size());
        return repeatBody(run, body, state, sequenceDepth, intervalTicks, extraRuns, 0);
    }

    private CompletableFuture<State> repeatBody(Run run,
            List<ActionAst> body,
            State state,
            int sequenceDepth,
            long intervalTicks,
            int extraRuns,
            int iteration) {
        return delayed(run, state, intervalTicks)
                .thenCompose(ready -> ready.terminal()
                        ? CompletableFuture.completedFuture(ready)
                        : walk(run, CompletableFuture.completedFuture(ready), body, sequenceDepth))
                .thenCompose(after -> iteration >= extraRuns || after.terminal()
                        ? CompletableFuture.completedFuture(after)
                        : repeatBody(run, body, after, sequenceDepth, intervalTicks, extraRuns,
                                iteration + 1));
    }

    /**
     * Waits {@code delayTicks} and then revalidates the pipeline before letting the body run.
     *
     * <p>The design requires caster, targets and owner all be rechecked after a delay, and requires the
     * result be {@code Skipped} rather than {@code Failure}: "the player logged off during the delay"
     * is not a configuration error.</p>
     */
    private CompletableFuture<State> delayed(Run run, State state, long delayTicks) {
        if (delayTicks <= 0L) {
            return CompletableFuture.completedFuture(state);
        }
        StageDispatcher.DispatchTarget target = dispatchTarget(ExecutionDomain.SERVER_GLOBAL,
                state.context(), probeSubject(state));
        return dispatcher.dispatch(run.owner(), target, delayTicks, "delay",
                        CoreActionStage.DEFAULT_TIMEOUT_MILLIS, run.cancellation(), () -> state)
                .handle((resumed, throwable) -> {
                    if (throwable != null) {
                        CoreActionOutcome outcome = throwableOutcome(throwable);
                        // A retired target or a disabled owner during the wait is a skip, not a failure.
                        if (outcome instanceof CoreActionOutcome.Failure failure
                                && (failure.kind() == CoreActionFailureKind.MISSING_CONTEXT
                                        || failure.kind() == CoreActionFailureKind.OWNER_DISABLED)) {
                            run.record("delay", PipelineOutcome.Status.SKIPPED, failure.reasonKey(), 0);
                            return state.stopped(failure.reasonKey());
                        }
                        return dispatchFailure(run, "delay", state, throwable);
                    }
                    return revalidate(run, resumed);
                });
    }

    private State revalidate(Run run, State state) {
        if (!run.owner().isEnabled()) {
            run.record("delay", PipelineOutcome.Status.SKIPPED, "action.run.owner_disabled", 0);
            return state.stopped("action.run.owner_disabled");
        }
        PipelineContext revalidated = state.context().revalidated();
        List<CoreActionSubject> survivors = revalidated.targets();
        if (!state.flow().isEmpty() && survivors.isEmpty()) {
            run.record("delay", PipelineOutcome.Status.SKIPPED, "action.run.all_targets_invalid", 0);
            return state.stopped("action.run.all_targets_invalid");
        }
        return state.withContext(revalidated).withFlow(survivors);
    }

    private CompletableFuture<State> runStageRun(Run run, List<ActionAst> batch, State state) {
        List<StageGroup.Plan> plans = new ArrayList<>(batch.size());
        for (ActionAst node : batch) {
            ActionAst.Stage stage = (ActionAst.Stage) node;
            StageInvoker.Handle handle = invoker.resolve(stage.id());
            if (handle == null) {
                run.record(stage.id(), PipelineOutcome.Status.FAILURE, "action.run.stage_unavailable", 0);
                return CompletableFuture.completedFuture(state.failed(CoreActionFailureKind.OWNER_DISABLED,
                        "action.run.stage_unavailable", Map.of("stage", stage.id())));
            }
            CoreActionSubject probe = probeSubject(state);
            ExecutionDomain domain = handle.foldable()
                    ? null
                    : invoker.domainOf(handle, state.context(), probe, stage.arguments());
            plans.add(new StageGroup.Plan(stage, handle, domain));
        }

        CompletableFuture<State> chain = CompletableFuture.completedFuture(state);
        for (StageGroup group : StageGroup.group(plans)) {
            chain = chain.thenCompose(current -> current.terminal()
                    ? CompletableFuture.completedFuture(current)
                    : runGroup(run, group, current));
        }
        return chain;
    }

    private static CoreActionSubject probeSubject(State state) {
        return state.flow().isEmpty() ? state.context().caster() : state.flow().get(0);
    }

    private CompletableFuture<State> step(Run run, ActionAst node, State state, int sequenceDepth) {
        if (run.cancellation().cancelled()) {
            return CompletableFuture.completedFuture(state.cancelled());
        }
        return switch (node) {
            case ActionAst.Branch branch -> branch(run, branch, state, sequenceDepth);
            case ActionAst.SequenceCall call -> sequence(run, call, state, sequenceDepth);
            // Reached only for a lone stage inside a branch or sequence body; the top-level walk batches
            // stages before they get here.
            case ActionAst.Stage stage -> runStageRun(run, List.of(stage), state);
        };
    }

    private CompletableFuture<State> runGroup(Run run, StageGroup group, State state) {
        if (group.perTarget()) {
            // A per-target group keeps one dispatch per target: the target owns the thread the stage
            // must run on, so these cannot be collapsed into a single dispatch.
            return runPerTargetGroup(run, group, state);
        }
        StageDispatcher.DispatchTarget target = dispatchTarget(
                group.domain() == null ? ExecutionDomain.SERVER_GLOBAL : group.domain(),
                state.context(), probeSubject(state));
        long timeout = groupTimeout(group);
        String name = groupName(group);
        return dispatcher.dispatch(run.owner(), target, 0L, name, timeout, run.cancellation(),
                        () -> runMembersInline(run, group, state))
                .handle((result, throwable) -> throwable != null
                        ? dispatchFailure(run, name, state, throwable)
                        : result);
    }

    /**
     * Runs every member of a group on the thread the group was dispatched to.
     *
     * <p>This is the point of grouping: the stages inside one group are already on the right thread, so
     * they run in sequence without going back to the scheduler.</p>
     */
    private State runMembersInline(Run run, StageGroup group, State state) {
        State current = state;
        for (StageGroup.Member member : group.members()) {
            if (current.terminal() || run.cancellation().cancelled()) {
                return current.terminal() ? current : current.cancelled();
            }
            StageInvoker.Handle handle = member.handle();
            Map<String, String> rendered = render(current.context(), member.node().arguments());
            current = switch (handle.kind()) {
                case SOURCE -> sourceResult(run, handle.id(), current,
                        invoker.invokeSource(handle, current.context(), arguments(handle, rendered)));
                case GATE -> gateResult(run, handle.id(), current,
                        invoker.invokeGate(handle, current.context(), current.flow(),
                                arguments(handle, rendered)));
                case ACTION -> actionResult(run, handle.id(), current, List.of(
                        invoker.invokeAction(handle, current.context().withTargets(List.of()),
                                arguments(handle, rendered))), 0);
            };
        }
        return current;
    }

    private CompletableFuture<State> runPerTargetGroup(Run run, StageGroup group, State state) {
        CompletableFuture<State> chain = CompletableFuture.completedFuture(state);
        for (StageGroup.Member member : group.members()) {
            StageGroup.Member current = member;
            chain = chain.thenCompose(inner -> inner.terminal()
                    ? CompletableFuture.completedFuture(inner)
                    : applyStageMember(run, current, inner));
        }
        return chain;
    }

    private CompletableFuture<State> applyStageMember(Run run, StageGroup.Member member, State state) {
        StageInvoker.Handle handle = member.handle();
        Map<String, String> raw = member.node().arguments();
        return switch (handle.kind()) {
            case SOURCE -> applySource(run, handle.id(), raw, state);
            case GATE -> applyGate(run, handle, raw, state);
            case ACTION -> applyAction(run, handle, raw, state);
        };
    }

    private static long groupTimeout(StageGroup group) {
        // The group shares one dispatch, so it needs one timeout. Taking the maximum keeps a slow stage
        // from being cut short by a faster neighbour; the design requires timeout be applied in exactly
        // one place, which is this dispatch.
        long timeout = 0L;
        for (StageGroup.Member member : group.members()) {
            timeout = Math.max(timeout, member.handle().timeoutMillis());
        }
        return timeout <= 0L ? 30_000L : timeout;
    }

    private static String groupName(StageGroup group) {
        StringBuilder builder = new StringBuilder();
        for (StageGroup.Member member : group.members()) {
            if (!builder.isEmpty()) {
                builder.append('+');
            }
            builder.append(member.handle().id());
        }
        return builder.toString();
    }

    private CompletableFuture<State> applySource(Run run,
            String stageId,
            Map<String, String> raw,
            State state) {
        StageInvoker.Handle handle = invoker.resolve(stageId);
        if (handle == null || handle.kind() != CoreStageKind.SOURCE) {
            run.record(stageId, PipelineOutcome.Status.FAILURE, "action.run.stage_unavailable", 0);
            return CompletableFuture.completedFuture(state.failed(CoreActionFailureKind.OWNER_DISABLED,
                    "action.run.stage_unavailable", Map.of("stage", Texts.toStringSafe(stageId))));
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
                // The one place every gate result converges, so recording `keep` here catches it wherever
                // it sits: inside a batched group, on its own dispatch, or inside a branch body.
                if (StaticValidator.KEEP_GATE.equals(stageId)) {
                    run.recordKept(passed.outbound());
                }
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
                run.record(handle.id(), PipelineOutcome.Status.SKIPPED, "action.run.no_target", 0);
                return CompletableFuture.completedFuture(state.stopped("action.run.no_target"));
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
        run.record(stageId, PipelineOutcome.Status.PARTIAL, "action.run.partial_targets", outcomes.size());
        return state.partial();
    }

    private CompletableFuture<State> branch(Run run,
            ActionAst.Branch node,
            State state,
            int sequenceDepth) {
        String condition = state.context().render(node.condition());
        Boolean evaluated = ExpressionEngine.evaluateBoolean(condition);
        if (evaluated == null) {
            run.record("if", PipelineOutcome.Status.FAILURE, "action.run.invalid_condition", state.flow().size());
            return CompletableFuture.completedFuture(state.failed(CoreActionFailureKind.INVALID_CONFIG,
                    "action.run.invalid_condition", Map.of("condition", condition)));
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
            run.record("run", PipelineOutcome.Status.FAILURE, "action.run.sequence_depth_exceeded", 0);
            return CompletableFuture.completedFuture(state.failed(CoreActionFailureKind.INVALID_CONFIG,
                    "action.run.sequence_depth_exceeded",
                    Map.of("sequence", node.sequence(), "maximum", limits.maxSequenceDepth())));
        }
        CompiledPipeline target = sequences.find(node.sequence());
        if (target == null) {
            run.record("run", PipelineOutcome.Status.FAILURE, "action.run.unknown_sequence", 0);
            return CompletableFuture.completedFuture(state.failed(CoreActionFailureKind.INVALID_CONFIG,
                    "action.run.unknown_sequence", Map.of("sequence", node.sequence())));
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
        // A branch body is a pipeline too, so it gets the same batching, grouping and timing handling.
        // Dispatching each of its stages separately would reintroduce per-stage scheduling inside every
        // branch.
        return walk(run, CompletableFuture.completedFuture(state), nodes, sequenceDepth);
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
            return CoreActionOutcome.failure(CoreActionFailureKind.TIMEOUT, "action.run.timeout");
        }
        if (cause instanceof java.util.concurrent.CancellationException) {
            return CoreActionOutcome.failure(CoreActionFailureKind.OWNER_DISABLED, "action.run.cancelled");
        }
        if (cause instanceof StageDispatcher.OwnerDisabledException) {
            return CoreActionOutcome.failure(CoreActionFailureKind.OWNER_DISABLED,
                    "action.run.owner_disabled", Map.of("error", Texts.toStringSafe(cause.getMessage())));
        }
        if (cause instanceof StageDispatcher.StageRetiredException) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.run.target_retired", Map.of("error", Texts.toStringSafe(cause.getMessage())));
        }
        return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR, "action.run.exception",
                Map.of("error", cause == null ? "" : Texts.toStringSafe(cause.getMessage())));
    }

    private static CoreActionOutcome.Failure firstFailure(List<CoreActionOutcome> outcomes) {
        for (CoreActionOutcome outcome : outcomes) {
            if (outcome instanceof CoreActionOutcome.Failure failure) {
                return failure;
            }
        }
        return new CoreActionOutcome.Failure(CoreActionFailureKind.INTERNAL_ERROR,
                "action.run.exception", Map.of());
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

    /**
     * One invocation's mutable bookkeeping: owner, cancellation, the per-stage log and the kept flow.
     *
     * <p>{@code keptFlow} is a single-element holder rather than a field because {@code Run} is a record: the
     * flow recorded by {@code keep} has to be replaceable while the record itself stays shallowly immutable,
     * the same shape {@code stageResults} already uses.</p>
     */
    private record Run(Plugin owner,
            CancellationSignal cancellation,
            List<PipelineOutcome.StageResult> stageResults,
            List<List<CoreActionSubject>> keptFlow) {

        private static Run start(Plugin owner, CancellationSignal cancellation) {
            return new Run(owner, cancellation, new ArrayList<>(), new ArrayList<>(1));
        }

        private void record(String stageId, PipelineOutcome.Status status, String reasonKey, int targetCount) {
            stageResults.add(new PipelineOutcome.StageResult(stageId, status, reasonKey, targetCount));
        }

        /**
         * Records the flow a {@code keep} gate saw.
         *
         * <p>Last write wins. {@code keep} means "the flow at this point", so a later {@code keep} replaces an
         * earlier one and a gate that narrows the flow after {@code keep} does not change what was recorded.</p>
         */
        private void recordKept(List<CoreActionSubject> flow) {
            keptFlow.clear();
            keptFlow.add(flow == null ? List.of() : List.copyOf(flow));
        }

        private List<CoreActionSubject> kept() {
            return keptFlow.isEmpty() ? List.of() : keptFlow.get(0);
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
            return failed(CoreActionFailureKind.OWNER_DISABLED, "action.run.cancelled", Map.of());
        }

        private State partial() {
            return new State(context, flow, stoppedReason, failureKind, reasonKey, args, true);
        }
    }
}
