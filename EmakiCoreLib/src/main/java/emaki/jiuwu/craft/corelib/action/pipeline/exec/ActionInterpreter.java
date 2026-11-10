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

public final class ActionInterpreter {

    private final StageInvoker invoker;
    private final StageDispatcher dispatcher;
    private final SequenceRepository sequences;
    private final PipelineLimits limits;
    final StepReporter reporter;
    private final boolean dryRun;

    public ActionInterpreter(@NotNull StageInvoker invoker,
            @NotNull StageDispatcher dispatcher,
            @Nullable SequenceRepository sequences,
            @Nullable PipelineLimits limits) {
        this(invoker, dispatcher, sequences, limits, StepReporter.NOOP, false);
    }

    public ActionInterpreter(@NotNull StageInvoker invoker,
            @NotNull StageDispatcher dispatcher,
            @Nullable SequenceRepository sequences,
            @Nullable PipelineLimits limits,
            @Nullable StepReporter reporter,
            boolean dryRun) {
        this.invoker = Objects.requireNonNull(invoker, "invoker");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.sequences = sequences == null ? SequenceRepository.empty() : sequences;
        this.limits = limits == null ? PipelineLimits.defaults() : limits;
        this.reporter = reporter == null ? StepReporter.NOOP : reporter;
        this.dryRun = dryRun;
    }

    public @NotNull CompletableFuture<PipelineOutcome> run(@NotNull Plugin owner,
            @NotNull CompiledPipeline pipeline,
            @NotNull PipelineContext context) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(pipeline, "pipeline");
        Objects.requireNonNull(context, "context");
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

    CompletableFuture<State> execute(Run run,
            List<ActionAst> nodes,
            PipelineContext context,
            boolean implicitSelf,
            int sequenceDepth) {
        CompletableFuture<State> chain = implicitSelf
                ? applySource(run, StaticValidator.SELF_SOURCE, Map.of(), State.start(context))
                : CompletableFuture.completedFuture(State.start(context));
        return walk(run, chain, nodes, sequenceDepth);
    }

    CompletableFuture<State> walk(Run run,
            CompletableFuture<State> chain,
            List<ActionAst> nodes,
            int sequenceDepth) {
        CompletableFuture<State> current = chain;

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

    CompletableFuture<State> flush(Run run,
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

    CompletableFuture<State> timed(Run run,
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
        ResolvedArguments arguments = ActionInterpreterSupport.arguments(handle, ActionInterpreterSupport.render(state.context(), stage.arguments()));
        boolean isAfter = StaticValidator.AFTER_STAGE.equals(stage.id());
        long intervalTicks = isAfter
                ? arguments.getDurationTicks("delay", 0L)
                : arguments.getDurationTicks("interval", 0L);
        if (intervalTicks <= 0L && !stage.positional().isEmpty()) {

            intervalTicks = Math.max(0L, ValueParsers.parseTicks(
                    state.context().render(stage.positional().get(0))));
        }
        if (body.isEmpty()) {
            run.record(stage.id(), PipelineOutcome.Status.SKIPPED, "action.run.timing_without_body", 0);
            return CompletableFuture.completedFuture(state.stopped("action.run.timing_without_body"));
        }

        int extraRuns = 0;
        if (!isAfter) {
            extraRuns = Math.max(0, arguments.getInt("times", 0));
            if (extraRuns == 0) {

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

    CompletableFuture<State> repeatBody(Run run,
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

    CompletableFuture<State> delayed(Run run, State state, long delayTicks) {
        if (delayTicks <= 0L) {
            return CompletableFuture.completedFuture(state);
        }
        StageDispatcher.DispatchTarget target = dispatchTarget(ExecutionDomain.SERVER_GLOBAL,
                state.context(), probeSubject(state));
        return dispatcher.dispatch(run.owner(), target, delayTicks, "delay",
                        CoreActionStage.DEFAULT_TIMEOUT_MILLIS, run.cancellation(), () -> state)
                .thenCompose(resumed -> revalidate(run, resumed))
                .handle((revalidated, throwable) -> {
                    if (throwable != null) {
                        CoreActionOutcome outcome = ActionInterpreterSupport.throwableOutcome(throwable);

                        if (outcome instanceof CoreActionOutcome.Failure failure
                                && (failure.kind() == CoreActionFailureKind.MISSING_CONTEXT
                                        || failure.kind() == CoreActionFailureKind.OWNER_DISABLED)) {
                            run.record("delay", PipelineOutcome.Status.SKIPPED, failure.reasonKey(), 0);
                            return state.stopped(failure.reasonKey());
                        }
                        return dispatchFailure(run, "delay", state, throwable);
                    }
                    return revalidated;
                });
    }

    CompletableFuture<State> revalidate(Run run, State state) { return new ActionInterpreterDelegate(this).revalidate(run, state); }

    CompletableFuture<Boolean> validateSubject(Run run, CoreActionSubject subject) {
        if (subject instanceof CoreActionSubject.OfEntity entity) {
            return validateSubject(run, subject, StageDispatcher.DispatchTarget.entity(entity.entity()));
        }
        if (subject instanceof CoreActionSubject.OfLocation location) {
            return validateSubject(run, subject, StageDispatcher.DispatchTarget.location(location.location()));
        }
        return CompletableFuture.completedFuture(false);
    }

    CompletableFuture<Boolean> validateSubject(Run run,
            CoreActionSubject subject,
            StageDispatcher.DispatchTarget target) {
        return dispatcher.dispatch(run.owner(), target, 0L, "revalidate",
                        CoreActionStage.DEFAULT_TIMEOUT_MILLIS, run.cancellation(), subject::valid)
                .handle((valid, throwable) -> throwable == null && Boolean.TRUE.equals(valid));
    }

    CompletableFuture<State> runStageRun(Run run, List<ActionAst> batch, State state) {
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

    static CoreActionSubject probeSubject(State state) { return ActionInterpreterDelegate.probeSubject(state); }

    CompletableFuture<State> step(Run run, ActionAst node, State state, int sequenceDepth) {
        if (run.cancellation().cancelled()) {
            return CompletableFuture.completedFuture(state.cancelled());
        }
        return switch (node) {
            case ActionAst.Branch branch -> branch(run, branch, state, sequenceDepth);
            case ActionAst.Weighted weighted -> weighted(run, weighted, state, sequenceDepth);
            case ActionAst.SequenceCall call -> sequence(run, call, state, sequenceDepth);
            case ActionAst.OnError onError -> onError(run, onError, state, sequenceDepth);
            case ActionAst.Union union -> runUnion(run, union, state);
            case ActionAst.Inline inline -> runNested(run, inline.nodes(), state, sequenceDepth);

            case ActionAst.Stage stage -> runStageRun(run, List.of(stage), state);
        };
    }

    CompletableFuture<State> runUnion(Run run,
            ActionAst.Union node,
            State state) {
        List<CoreActionSubject> merged = new ArrayList<>();
        CompletableFuture<State> chain = CompletableFuture.completedFuture(state);
        for (ActionAst source : node.sources()) {
            if (!(source instanceof ActionAst.Stage stage)) {
                continue;
            }
            chain = chain.thenCompose(current -> {
                if (current.terminal() && current.failureKind() != null) {
                    return CompletableFuture.completedFuture(current);
                }
                State base = current.failureKind() == null ? current : current.recovered();
                return applySource(run, stage.id(), stage.arguments(), base)
                        .thenApply(after -> {
                            if (after.failureKind() != null) {
                                return after;
                            }
                            if (after.stoppedReason() != null) {
                                return after.recovered();
                            }
                            merged.addAll(after.flow());
                            return after;
                        });
            });
        }
        return chain.thenApply(current -> {
            if (current.failureKind() != null) {
                return current;
            }
            LinkedHashSet<CoreActionSubject> unique =
                    new LinkedHashSet<>(merged);
            return current.recovered().withFlow(new ArrayList<>(unique));
        });
    }

    CompletableFuture<State> onError(Run run,
            ActionAst.OnError node,
            State state,
            int sequenceDepth) {
        return walk(run, CompletableFuture.completedFuture(state), node.body(), sequenceDepth)
                .thenCompose(afterBody -> {
                    if (afterBody.failureKind() == null) {
                        return CompletableFuture.completedFuture(afterBody);
                    }
                    run.record(PipelineParser.ON_ERROR, PipelineOutcome.Status.SUCCESS,
                            "", afterBody.flow().size());
                    reporter.onError(PipelineParser.ON_ERROR, afterBody.failureKind().name(),
                            afterBody.reasonKey());
                    Map<String, Object> variables = new LinkedHashMap<>();
                    variables.put("error", afterBody.failureKind().name());
                    variables.put("error_reason", afterBody.reasonKey());
                    State handlerStart = afterBody.recovered()
                            .withContext(afterBody.context().withVariables(variables));
                    return walk(run, CompletableFuture.completedFuture(handlerStart),
                            node.handler(), sequenceDepth);
                });
    }

    CompletableFuture<State> runGroup(Run run, StageGroup group, State state) {
        if (group.perTarget()) {

            return runPerTargetGroup(run, group, state);
        }
        StageDispatcher.DispatchTarget target = dispatchTarget(
                group.domain() == null ? ExecutionDomain.SERVER_GLOBAL : group.domain(),
                state.context(), probeSubject(state));
        long timeout = ActionInterpreterSupport.groupTimeout(group);
        String name = ActionInterpreterSupport.groupName(group);
        return dispatcher.dispatch(run.owner(), target, 0L, name, timeout, run.cancellation(),
                        () -> runMembersInline(run, group, state))
                .handle((result, throwable) -> throwable != null
                        ? dispatchFailure(run, name, state, throwable)
                        : result);
    }

    State runMembersInline(Run run, StageGroup group, State state) {
        State current = state;
        for (StageGroup.Member member : group.members()) {
            if (current.terminal() || run.cancellation().cancelled()) {
                return current.terminal() ? current : current.cancelled();
            }
            StageInvoker.Handle handle = member.handle();
            Map<String, String> rendered = ActionInterpreterSupport.render(current.context(), member.node().arguments());
            current = switch (handle.kind()) {
                case SOURCE -> sourceResult(run, handle.id(), current,
                        invoker.invokeSource(handle, current.context(), ActionInterpreterSupport.arguments(handle, rendered)));
                case GATE -> gateResult(run, handle.id(), current,
                        invoker.invokeGate(handle, current.context(), current.flow(),
                                ActionInterpreterSupport.arguments(handle, rendered)));
                case ACTION -> actionResult(run, handle.id(), current, List.of(
                        invoker.invokeAction(handle, current.context().withTargets(List.of()),
                                ActionInterpreterSupport.arguments(handle, rendered))), 0);
            };
        }
        return current;
    }

    CompletableFuture<State> runPerTargetGroup(Run run, StageGroup group, State state) {
        CompletableFuture<State> chain = CompletableFuture.completedFuture(state);
        for (StageGroup.Member member : group.members()) {
            StageGroup.Member current = member;
            chain = chain.thenCompose(inner -> inner.terminal()
                    ? CompletableFuture.completedFuture(inner)
                    : applyStageMember(run, current, inner));
        }
        return chain;
    }

    CompletableFuture<State> applyStageMember(Run run, StageGroup.Member member, State state) {
        StageInvoker.Handle handle = member.handle();
        Map<String, String> raw = member.node().arguments();
        return switch (handle.kind()) {
            case SOURCE -> applySource(run, handle.id(), raw, state);
            case GATE -> applyGate(run, handle, raw, state);
            case ACTION -> retryable(run, handle, member.node(), raw, state);
        };
    }

    CompletableFuture<State> retryable(Run run,
            StageInvoker.Handle handle,
            ActionAst.Stage node,
            Map<String, String> raw,
            State state) {
        int maxRetries = Math.min(node.retryTimes(), limits.maxRetryTimes());
        if (maxRetries <= 0 || dryRun) {
            return applyAction(run, handle, raw, state);
        }
        return retryAttempt(run, handle, node, raw, state, 0, maxRetries);
    }

    CompletableFuture<State> retryAttempt(Run run,
            StageInvoker.Handle handle,
            ActionAst.Stage node,
            Map<String, String> raw,
            State state,
            int attempt,
            int maxRetries) {
        return applyAction(run, handle, raw, state, attempt)
                .thenCompose(afterAttempt -> {
                    if (afterAttempt.failureKind() == null || attempt >= maxRetries) {
                        return CompletableFuture.completedFuture(afterAttempt);
                    }
                    reporter.retry(handle.id(), attempt + 1, maxRetries, afterAttempt.reasonKey());
                    State retriable = afterAttempt.recovered();
                    return delayed(run, retriable, node.retryDelayTicks())
                            .thenCompose(ready -> retryAttempt(run, handle, node, raw, ready,
                                    attempt + 1, maxRetries));
                });
    }


    CompletableFuture<State> applySource(Run run,
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
                invoker.domainOf(handle, context, context.caster(), raw), context, context.caster(), true);
        return dispatcher.dispatch(run.owner(), target, 0L, handle.id(), handle.timeoutMillis(),
                run.cancellation(),
                () -> invoker.invokeSource(handle, context, ActionInterpreterSupport.arguments(handle, ActionInterpreterSupport.render(context, raw))))
                .handle((result, throwable) -> throwable != null
                        ? dispatchFailure(run, handle.id(), state, throwable)
                        : sourceResult(run, handle.id(), state, result));
    }

    State sourceResult(Run run, String stageId, State state, CoreSourceResult result) {
        return switch (result) {
            case CoreSourceResult.Selected selected -> {
                run.record(stageId, PipelineOutcome.Status.SUCCESS, "", selected.subjects().size());
                reporter.source(stageId, selected.subjects().size(), null);
                yield state.withFlow(selected.subjects());
            }
            case CoreSourceResult.Empty empty -> {
                run.record(stageId, PipelineOutcome.Status.SKIPPED, empty.reasonKey(), 0);
                reporter.source(stageId, 0, empty.reasonKey());
                yield state.stopped(empty.reasonKey());
            }
            case CoreSourceResult.Invalid invalid -> {
                run.record(stageId, PipelineOutcome.Status.FAILURE, invalid.reasonKey(), 0);
                reporter.source(stageId, 0, invalid.reasonKey());
                yield state.failed(CoreActionFailureKind.INVALID_CONFIG, invalid.reasonKey(), invalid.args());
            }
        };
    }

    CompletableFuture<State> applyGate(Run run,
            StageInvoker.Handle handle,
            Map<String, String> raw,
            State state) {
        PipelineContext context = state.context();
        List<CoreActionSubject> inbound = state.flow();
        CoreActionSubject probe = inbound.isEmpty() ? context.caster() : inbound.get(0);
        StageDispatcher.DispatchTarget target = dispatchTarget(
                invoker.domainOf(handle, context, probe, raw), context, probe, true);
        return dispatcher.dispatch(run.owner(), target, 0L, handle.id(), handle.timeoutMillis(),
                run.cancellation(),
                () -> invoker.invokeGate(handle, context, inbound, ActionInterpreterSupport.arguments(handle, ActionInterpreterSupport.render(context, raw))))
                .handle((result, throwable) -> throwable != null
                        ? dispatchFailure(run, handle.id(), state, throwable)
                        : gateResult(run, handle.id(), state, result));
    }

    State gateResult(Run run, String stageId, State state, CoreGateResult result) {
        return switch (result) {
            case CoreGateResult.Passed passed -> {
                run.record(stageId, PipelineOutcome.Status.SUCCESS, "", passed.outbound().size());
                reporter.gate(stageId, state.flow().size(), passed.outbound().size(), null);

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
                reporter.gate(stageId, state.flow().size(), 0, halted.reasonKey());
                yield state.stopped(halted.reasonKey());
            }
            case CoreGateResult.Invalid invalid -> {
                run.record(stageId, PipelineOutcome.Status.FAILURE, invalid.reasonKey(), state.flow().size());
                reporter.gate(stageId, state.flow().size(), 0, invalid.reasonKey());
                yield state.failed(CoreActionFailureKind.INVALID_CONFIG, invalid.reasonKey(), invalid.args());
            }
        };
    }

    CompletableFuture<State> applyAction(Run run,
            StageInvoker.Handle handle,
            Map<String, String> raw,
            State state) {
        return applyAction(run, handle, raw, state, 0);
    }

    CompletableFuture<State> applyAction(Run run,
            StageInvoker.Handle handle,
            Map<String, String> raw,
            State state,
            int attempt) {
        PipelineContext context = state.context();
        if (dryRun) {
            Map<String, String> rendered = ActionInterpreterSupport.render(context, raw);
            reporter.action(handle.id(), handle.kind(), rendered, 0, 0, state.flow().isEmpty()
                    ? (handle.targetRequirement().requiresTarget() ? 0 : 1)
                    : state.flow().size(), null);
            run.record(handle.id(), PipelineOutcome.Status.SKIPPED, "action.debug.dry_run",
                    state.flow().size());
            return CompletableFuture.completedFuture(state);
        }
        if (handle.targetRequirement() == CoreTargetRequirement.NONE) {
            return invokeOnce(run, handle, raw, state, context.withTargets(List.of()), -1)
                    .thenApply(outcome -> actionResult(run, handle.id(), state, List.of(outcome), 0));
        }

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

    CompletableFuture<CoreActionOutcome> invokeOnce(Run run,
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

        return dispatcher.dispatch(run.owner(), target, 0L, handle.id(), handle.timeoutMillis(),
                run.cancellation(),
                () -> {
                    if (target.domain() == ExecutionDomain.ENTITY
                            && subject instanceof CoreActionSubject.OfEntity
                            && !subject.valid()) {
                        return CoreActionOutcome.skipped("action.run.all_targets_invalid");
                    }
                    return invoker.invokeAction(handle, invocationContext,
                            ActionInterpreterSupport.arguments(handle, ActionInterpreterSupport.render(invocationContext, raw)));
                })
                .handle((outcome, throwable) -> {
                    if (throwable == null) {
                        return outcome;
                    }
                    CoreActionOutcome failure = ActionInterpreterSupport.throwableOutcome(throwable);
                    return failure instanceof CoreActionOutcome.Failure failed
                            && failed.kind() == CoreActionFailureKind.MISSING_CONTEXT
                            && "action.run.target_retired".equals(failed.reasonKey())
                            ? CoreActionOutcome.skipped("action.run.all_targets_invalid")
                            : failure;
                });
    }

    State actionResult(Run run,
            String stageId,
            State state,
            List<CoreActionOutcome> outcomes,
            int skippedTargets) { return new ActionInterpreterDelegate(this).actionResult(run, stageId, state, outcomes, skippedTargets); }

    CompletableFuture<State> branch(Run run,
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

    CompletableFuture<State> weighted(Run run,
            ActionAst.Weighted node,
            State state,
            int sequenceDepth) { return new ActionInterpreterDelegate(this).weighted(run, node, state, sequenceDepth); }

    CompletableFuture<State> sequence(Run run,
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
                .isolated(ActionInterpreterSupport.render(state.context(), node.parameters()));
        return execute(run, target.nodes(), isolated, target.implicitSelfSource(), sequenceDepth + 1)
                .thenApply(inner -> mergeSequence(state, inner));
    }

    CompletableFuture<State> runNested(Run run,
            List<ActionAst> nodes,
            State state,
            int sequenceDepth) {

        return walk(run, CompletableFuture.completedFuture(state), nodes, sequenceDepth);
    }

    State mergeSequence(State caller, State callee) { return new ActionInterpreterDelegate(this).mergeSequence(caller, callee); }

    PipelineOutcome summarise(Run run, State state) { return new ActionInterpreterDelegate(this).summarise(run, state); }

    State dispatchFailure(Run run, String stageId, State state, Throwable throwable) { return new ActionInterpreterDelegate(this).dispatchFailure(run, stageId, state, throwable); }


    private static StageDispatcher.DispatchTarget dispatchTarget(ExecutionDomain domain,
            PipelineContext context,
            CoreActionSubject subject) {
        return dispatchTarget(domain, context, subject, false);
    }

    private static StageDispatcher.DispatchTarget dispatchTarget(ExecutionDomain domain,
            PipelineContext context,
            CoreActionSubject subject,
            boolean originFirst) {
        return switch (domain) {
            case SERVER_GLOBAL -> StageDispatcher.DispatchTarget.global();
            case ASYNC_COMPUTE -> StageDispatcher.DispatchTarget.async();
            case PHYSICAL_FILE -> StageDispatcher.DispatchTarget.physicalFile();
            case ENTITY -> StageDispatcher.DispatchTarget.entity(ActionInterpreterSupport.entityTarget(context, subject));
            case LOCATION_REGION -> locationTarget(context, subject, originFirst);
        };
    }


    private static StageDispatcher.DispatchTarget locationTarget(PipelineContext context,
            CoreActionSubject subject,
            boolean originFirst) { return ActionInterpreterDelegate.locationTarget(context, subject, originFirst); }

    record Run(Plugin owner,
            CancellationSignal cancellation,
            List<PipelineOutcome.StageResult> stageResults,
            List<List<CoreActionSubject>> keptFlow) {

        static Run start(Plugin owner, CancellationSignal cancellation) {
            return new Run(owner, cancellation, new ArrayList<>(), new ArrayList<>(1));
        }

        void record(String stageId, PipelineOutcome.Status status, String reasonKey, int targetCount) {
            stageResults.add(new PipelineOutcome.StageResult(stageId, status, reasonKey, targetCount));
        }

        private void recordKept(List<CoreActionSubject> flow) {
            keptFlow.clear();
            keptFlow.add(flow == null ? List.of() : List.copyOf(flow));
        }

        List<CoreActionSubject> kept() {
            return keptFlow.isEmpty() ? List.of() : keptFlow.get(0);
        }
    }

    record State(PipelineContext context,
            List<CoreActionSubject> flow,
            @Nullable String stoppedReason,
            @Nullable CoreActionFailureKind failureKind,
            String reasonKey,
            Map<String, Object> args,
            boolean partialSeen) {

        static State start(PipelineContext context) {
            return new State(context, context.targets(), null, null, "", Map.of(), false);
        }

        private boolean terminal() {
            return stoppedReason != null || failureKind != null;
        }

        State withContext(PipelineContext newContext) {
            return new State(newContext, flow, stoppedReason, failureKind, reasonKey, args, partialSeen);
        }

        State withFlow(List<CoreActionSubject> newFlow) {
            List<CoreActionSubject> copy = newFlow == null ? List.of() : List.copyOf(newFlow);
            return new State(context.withTargets(copy), copy, stoppedReason, failureKind,
                    reasonKey, args, partialSeen);
        }

        State stopped(String reason) {
            return new State(context, flow, reason == null ? "" : reason, failureKind,
                    reasonKey, args, partialSeen);
        }

        State failed(CoreActionFailureKind kind, String reason, Map<String, Object> failureArgs) {
            return new State(context, flow, stoppedReason,
                    kind == null ? CoreActionFailureKind.INTERNAL_ERROR : kind,
                    reason == null ? "" : reason,
                    failureArgs == null ? Map.of() : Map.copyOf(failureArgs),
                    partialSeen);
        }

        State cancelled() {
            return failed(CoreActionFailureKind.OWNER_DISABLED, "action.run.cancelled", Map.of());
        }

        State recovered() {
            return new State(context, flow, null, null, "", Map.of(), partialSeen);
        }

        State partial() {
            return new State(context, flow, stoppedReason, failureKind, reasonKey, args, true);
        }
    }
}
