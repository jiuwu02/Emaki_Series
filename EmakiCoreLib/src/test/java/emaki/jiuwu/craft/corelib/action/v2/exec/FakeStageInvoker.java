package emaki.jiuwu.craft.corelib.action.v2.exec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/** Scripted {@link StageInvoker} that records what the interpreter asked it to do. */
final class FakeStageInvoker implements StageInvoker {

    interface SourceBody {
        CoreSourceResult select(CoreStageContext context, CoreResolvedArguments arguments);
    }

    interface GateBody {
        CoreGateResult apply(CoreStageContext context,
                List<CoreActionSubject> inbound,
                CoreResolvedArguments arguments);
    }

    interface ActionBody {
        CoreActionOutcome execute(CoreStageContext context, CoreResolvedArguments arguments);
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final List<String> invocationLog = new ArrayList<>();

    private record Entry(CoreStageKind kind,
            List<CoreStageParameter> parameters,
            CoreTargetRequirement requirement,
            ExecutionDomain domain,
            boolean foldable,
            SourceBody source,
            GateBody gate,
            ActionBody action) {
    }

    FakeStageInvoker source(String id, SourceBody body, CoreStageParameter... parameters) {
        return source(id, ExecutionDomain.SERVER_GLOBAL, body, parameters);
    }

    FakeStageInvoker source(String id,
            ExecutionDomain domain,
            SourceBody body,
            CoreStageParameter... parameters) {
        entries.put(id, new Entry(CoreStageKind.SOURCE, List.of(parameters), CoreTargetRequirement.NONE,
                domain, false, body, null, null));
        return this;
    }

    FakeStageInvoker gate(String id, GateBody body, CoreStageParameter... parameters) {
        return gate(id, ExecutionDomain.SERVER_GLOBAL, false, body, parameters);
    }

    FakeStageInvoker gate(String id,
            ExecutionDomain domain,
            boolean foldable,
            GateBody body,
            CoreStageParameter... parameters) {
        entries.put(id, new Entry(CoreStageKind.GATE, List.of(parameters), CoreTargetRequirement.NONE,
                domain, foldable, null, body, null));
        return this;
    }

    FakeStageInvoker action(String id,
            CoreTargetRequirement requirement,
            ActionBody body,
            CoreStageParameter... parameters) {
        return action(id, requirement, ExecutionDomain.SERVER_GLOBAL, body, parameters);
    }

    FakeStageInvoker action(String id,
            CoreTargetRequirement requirement,
            ExecutionDomain domain,
            ActionBody body,
            CoreStageParameter... parameters) {
        entries.put(id, new Entry(CoreStageKind.ACTION, List.of(parameters), requirement,
                domain, false, null, null, body));
        return this;
    }

    List<String> invocationLog() {
        return List.copyOf(invocationLog);
    }

    @Override
    public @Nullable Handle resolve(@Nullable String id) {
        Entry entry = id == null ? null : entries.get(id);
        return entry == null
                ? null
                : new Handle(id, entry.kind(), entry.parameters(), entry.requirement(), 30_000L,
                        entry.foldable());
    }

    @Override
    public @NotNull ExecutionDomain domainOf(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull CoreActionSubject target,
            @NotNull Map<String, String> rawArguments) {
        Entry entry = entries.get(handle.id());
        return entry == null ? ExecutionDomain.SERVER_GLOBAL : entry.domain();
    }

    @Override
    public @NotNull CoreSourceResult invokeSource(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        invocationLog.add(handle.id());
        return entries.get(handle.id()).source().select(context, arguments);
    }

    @Override
    public @NotNull CoreGateResult invokeGate(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        invocationLog.add(handle.id());
        return entries.get(handle.id()).gate().apply(context, inbound, arguments);
    }

    @Override
    public @NotNull CoreActionOutcome invokeAction(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        invocationLog.add(handle.id() + "#" + context.currentTargetIndex());
        return entries.get(handle.id()).action().execute(context, arguments);
    }
}
