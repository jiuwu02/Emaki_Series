package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.StageResolver;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/** Read-only compiler view over the live stage registry. */
public final class RegistryStageResolver implements StageResolver {

    private final StageRegistry registry;

    /**
     * Creates a registry-backed resolver.
     *
     * @param registry the live registry
     */
    public RegistryStageResolver(@NotNull StageRegistry registry) {
        this.registry = java.util.Objects.requireNonNull(registry, "registry");
    }

    @Override
    public @NotNull Resolution resolve(@Nullable String id) {
        StageLookup lookup = lookup(id);
        if (lookup instanceof StageLookup.OwnerDisabled disabled) {
            return Resolution.disabled(disabled.kind(), disabled.ownerName());
        }
        if (!(lookup instanceof StageLookup.Found found)) {
            return Resolution.unknown();
        }
        RegisteredStage registered = found.entry();
        CoreTargetRequirement targetRequirement = registered.stage() instanceof CoreActionStage action
                ? action.targetRequirement()
                : CoreTargetRequirement.NONE;
        return Resolution.found(registered.kind(), parameters(registered), requiredContext(registered),
                providedContext(registered), providedVariables(registered), targetRequirement,
                probeDomain(registered));
    }

    @Override
    public @NotNull List<String> knownIds(@NotNull CoreStageKind kind) {
        return switch (kind) {
            case SOURCE -> registry.sources().ids();
            case GATE -> registry.gates().ids();
            case ACTION -> registry.actions().ids();
        };
    }

    private StageLookup lookup(String id) {
        StageLookup source = registry.sources().lookup(id);
        if (!(source instanceof StageLookup.Unknown)) {
            return source;
        }
        StageLookup gate = registry.gates().lookup(id);
        if (!(gate instanceof StageLookup.Unknown)) {
            return gate;
        }
        return registry.actions().lookup(id);
    }

    private List<CoreStageParameter> parameters(RegisteredStage registered) {
        return switch (registered.kind()) {
            case SOURCE -> ((CoreActionSource) registered.stage()).parameters();
            case GATE -> ((CoreActionGate) registered.stage()).parameters();
            case ACTION -> ((CoreActionStage) registered.stage()).parameters();
        };
    }

    private Set<CoreActionKey<?>> requiredContext(RegisteredStage registered) {
        return registered.stage() instanceof CoreActionStage action
                ? action.requiredContext()
                : Set.of();
    }

    private Set<CoreActionKey<?>> providedContext(RegisteredStage registered) {
        return registered.stage() instanceof CoreActionGate gate
                ? gate.providedContext()
                : Set.of();
    }

    private Set<String> providedVariables(RegisteredStage registered) {
        return registered.stage() instanceof CoreActionGate gate
                ? gate.providedVariables()
                : Set.of();
    }

    private ExecutionDomain probeDomain(RegisteredStage registered) {
        CoreStagePlanningContext planning = CoreStagePlanningContext.probe();
        try {
            return switch (registered.kind()) {
                case SOURCE -> toDomain(((CoreActionSource) registered.stage()).executionTarget(planning));
                case GATE -> switch (((CoreActionGate) registered.stage()).threadNeed()) {
                    case PURE -> ExecutionDomain.ASYNC_COMPUTE;
                    case NEEDS_ENTITY_READ -> ExecutionDomain.ENTITY;
                    case NEEDS_REGION_READ -> ExecutionDomain.LOCATION_REGION;
                };
                case ACTION -> toDomain(((CoreActionStage) registered.stage()).executionTarget(planning));
            };
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private ExecutionDomain toDomain(CoreActionExecutionTarget target) {
        CoreActionExecutionDomain domain = target == null ? CoreActionExecutionDomain.UNDECLARED : target.domain();
        return switch (domain) {
            case UNDECLARED -> null;
            case SERVER_GLOBAL -> ExecutionDomain.SERVER_GLOBAL;
            case CONTEXT_ENTITY -> ExecutionDomain.ENTITY;
            case LOCATION_REGION -> ExecutionDomain.LOCATION_REGION;
            case ASYNC_COMPUTE -> ExecutionDomain.ASYNC_COMPUTE;
        };
    }
}
