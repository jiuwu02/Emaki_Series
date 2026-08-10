package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.registry.RegisteredStage;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageLookup;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreGateResult;
import emaki.jiuwu.craft.corelib.api.action.CoreGateThread;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreSourceResult;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/** Runs stages that live in the single CoreLib registry. */
public final class RegistryStageInvoker implements StageInvoker {

    private final StageRegistry registry;

    /**
     * Creates a registry-backed invoker.
     *
     * @param registry the live registry
     */
    public RegistryStageInvoker(@NotNull StageRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public @Nullable Handle resolve(@Nullable String id) {
        RegisteredStage entry = live(id);
        if (entry == null) {
            return null;
        }
        return switch (entry.kind()) {
            case SOURCE -> {
                CoreActionSource source = (CoreActionSource) entry.stage();
                yield new Handle(entry.id(), CoreStageKind.SOURCE, source.parameters(),
                        CoreTargetRequirement.NONE, CoreActionStage.DEFAULT_TIMEOUT_MILLIS);
            }
            case GATE -> {
                CoreActionGate gate = (CoreActionGate) entry.stage();
                yield new Handle(entry.id(), CoreStageKind.GATE, gate.parameters(),
                        CoreTargetRequirement.NONE, CoreActionStage.DEFAULT_TIMEOUT_MILLIS,
                        gate.threadNeed() == CoreGateThread.PURE);
            }
            case ACTION -> {
                CoreActionStage action = (CoreActionStage) entry.stage();
                yield new Handle(entry.id(), CoreStageKind.ACTION, action.parameters(),
                        action.targetRequirement(), action.timeoutMillis());
            }
        };
    }

    @Override
    public @NotNull ExecutionDomain domainOf(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull CoreActionSubject target,
            @NotNull Map<String, String> rawArguments) {
        RegisteredStage entry = live(handle.id());
        if (entry == null) {
            return ExecutionDomain.SERVER_GLOBAL;
        }
        CoreStagePlanningContext planning = new CoreStagePlanningContext(context.caster(), target,
                context.phase(), rawArguments);
        return switch (entry.kind()) {
            case SOURCE -> toDomain(((CoreActionSource) entry.stage()).executionTarget(planning));
            case GATE -> switch (((CoreActionGate) entry.stage()).threadNeed()) {
                // A PURE gate reports the global domain only as a standalone fallback. The interpreter
                // folds it into whichever domain its neighbours use, so this value is rarely consulted.
                case PURE -> ExecutionDomain.SERVER_GLOBAL;
                case NEEDS_ENTITY_READ -> ExecutionDomain.ENTITY;
                case NEEDS_REGION_READ -> ExecutionDomain.LOCATION_REGION;
            };
            case ACTION -> toDomain(((CoreActionStage) entry.stage()).executionTarget(planning));
        };
    }

    @Override
    public @NotNull CoreSourceResult invokeSource(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        RegisteredStage entry = live(handle.id());
        if (entry == null || entry.kind() != CoreStageKind.SOURCE) {
            return CoreSourceResult.invalid("action.run.stage_unavailable");
        }
        return ((CoreActionSource) entry.stage()).select(context, arguments);
    }

    @Override
    public @NotNull CoreGateResult invokeGate(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull List<CoreActionSubject> inbound,
            @NotNull CoreResolvedArguments arguments) {
        RegisteredStage entry = live(handle.id());
        if (entry == null || entry.kind() != CoreStageKind.GATE) {
            return CoreGateResult.invalid("action.run.stage_unavailable");
        }
        return ((CoreActionGate) entry.stage()).apply(context, inbound, arguments);
    }

    @Override
    public @NotNull CoreActionOutcome invokeAction(@NotNull Handle handle,
            @NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        RegisteredStage entry = live(handle.id());
        if (entry == null || entry.kind() != CoreStageKind.ACTION) {
            return CoreActionOutcome.failure(
                    CoreActionFailureKind.OWNER_DISABLED,
                    "action.run.stage_unavailable");
        }
        return ((CoreActionStage) entry.stage()).execute(context, arguments);
    }

    private RegisteredStage live(String id) {
        StageLookup source = registry.sources().lookup(id);
        if (source instanceof StageLookup.Found found) {
            return found.entry();
        }
        StageLookup gate = registry.gates().lookup(id);
        if (gate instanceof StageLookup.Found found) {
            return found.entry();
        }
        return registry.actions().lookup(id) instanceof StageLookup.Found found ? found.entry() : null;
    }

    private static ExecutionDomain toDomain(CoreActionExecutionTarget target) {
        if (target == null) {
            return ExecutionDomain.SERVER_GLOBAL;
        }
        return switch (target.domain()) {
            // UNDECLARED cannot reach here: registration rejects it. Mapping it to global keeps this
            // switch total without inventing a runtime inference rule.
            case UNDECLARED, SERVER_GLOBAL -> ExecutionDomain.SERVER_GLOBAL;
            case CONTEXT_ENTITY -> ExecutionDomain.ENTITY;
            case LOCATION_REGION -> ExecutionDomain.LOCATION_REGION;
            case ASYNC_COMPUTE -> ExecutionDomain.ASYNC_COMPUTE;
        };
    }
}
