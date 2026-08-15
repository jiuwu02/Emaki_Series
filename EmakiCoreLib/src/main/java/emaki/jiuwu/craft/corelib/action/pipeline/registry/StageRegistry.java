package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionGate;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSource;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageRegistration;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class StageRegistry {

    private final StageTable sources = new StageTable(CoreStageKind.SOURCE);
    private final StageTable gates = new StageTable(CoreStageKind.GATE);
    private final StageTable actions = new StageTable(CoreStageKind.ACTION);
    private final ActionKeyRegistry keys = new ActionKeyRegistry();

    public @NotNull StageTable sources() {
        return sources;
    }

    public @NotNull StageTable gates() {
        return gates;
    }

    public @NotNull StageTable actions() {
        return actions;
    }

    public @NotNull ActionKeyRegistry keys() {
        return keys;
    }

    public @NotNull CoreStageRegistration registerSource(@Nullable Plugin owner, @Nullable CoreActionSource source) {
        if (source == null || Texts.isBlank(source.id())) {
            return failed(CoreStageKind.SOURCE, "action.register.blank_id");
        }
        StageRegistrationResult domainCheck = checkDomain(source.id(), CoreStageKind.SOURCE,
                safeTarget(() -> source.executionTarget(planningProbe())), CoreTargetRequirement.OPTIONAL);
        if (!domainCheck.accepted()) {
            return failed(CoreStageKind.SOURCE, domainCheck.reasonKey());
        }
        return install(sources, owner, source.id(), source);
    }

    public @NotNull CoreStageRegistration registerGate(@Nullable Plugin owner, @Nullable CoreActionGate gate) {
        if (gate == null || Texts.isBlank(gate.id())) {
            return failed(CoreStageKind.GATE, "action.register.blank_id");
        }
        return install(gates, owner, gate.id(), gate);
    }

    public @NotNull CoreStageRegistration registerAction(@Nullable Plugin owner, @Nullable CoreActionStage stage) {
        if (stage == null || Texts.isBlank(stage.id())) {
            return failed(CoreStageKind.ACTION, "action.register.blank_id");
        }
        StageRegistrationResult domainCheck = checkDomain(stage.id(), CoreStageKind.ACTION,
                safeTarget(() -> stage.executionTarget(planningProbe())), stage.targetRequirement());
        if (!domainCheck.accepted()) {
            return failed(CoreStageKind.ACTION, domainCheck.reasonKey());
        }
        for (var key : stage.requiredContext()) {
            keys.declare(key, stage.id());
        }
        return install(actions, owner, stage.id(), stage);
    }

    public int revokeAll(@Nullable Plugin owner) {
        return sources.revokeAll(owner) + gates.revokeAll(owner) + actions.revokeAll(owner);
    }

    public @Nullable CoreStageKind kindOf(@Nullable String id) {
        if (sources.lookup(id) instanceof StageLookup.Found) {
            return CoreStageKind.SOURCE;
        }
        if (gates.lookup(id) instanceof StageLookup.Found) {
            return CoreStageKind.GATE;
        }
        if (actions.lookup(id) instanceof StageLookup.Found) {
            return CoreStageKind.ACTION;
        }
        return null;
    }

    public @NotNull Map<CoreStageKind, Integer> counts() {
        return Map.of(CoreStageKind.SOURCE, sources.size(),
                CoreStageKind.GATE, gates.size(),
                CoreStageKind.ACTION, actions.size());
    }

    public void clear() {
        sources.clear();
        gates.clear();
        actions.clear();
        keys.clear();
    }

    public static @NotNull StageRegistrationResult checkDomain(@Nullable String id,
            @Nullable CoreStageKind kind,
            @Nullable CoreActionExecutionTarget target,
            @Nullable CoreTargetRequirement requirement) {
        if (target == null || target.domain() == CoreActionExecutionDomain.UNDECLARED) {
            return StageRegistrationResult.rejected(id, kind, "action.register.undeclared_domain",
                    Map.of("stage", Texts.toStringSafe(id)));
        }
        if (target.domain() == CoreActionExecutionDomain.ASYNC_COMPUTE
                && requirement != null && requirement != CoreTargetRequirement.NONE) {
            return StageRegistrationResult.rejected(id, kind, "action.register.async_needs_target",
                    Map.of("stage", Texts.toStringSafe(id), "requirement", requirement.name()));
        }
        return StageRegistrationResult.accepted(Texts.lower(id), kind);
    }

    private CoreStageRegistration install(StageTable table, Plugin owner, String id, Object stage) {
        RegisteredStage entry = table.register(id, stage, owner);
        if (entry == null) {
            String firstOwner = table.ownerNameOf(id);
            return new Handle(table, Texts.lower(id), table.kind(), -1L, false,
                    Texts.isBlank(firstOwner) ? "action.register.duplicate_id"
                            : "action.register.duplicate_id_owned_by:" + firstOwner);
        }
        return new Handle(table, entry.id(), table.kind(), entry.generation(), true, "");
    }

    private static CoreStageRegistration failed(CoreStageKind kind, String reasonKey) {
        return CoreStageRegistration.unavailable(kind, reasonKey);
    }

    private static CoreActionExecutionTarget safeTarget(Supplier<CoreActionExecutionTarget> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static CoreStagePlanningContext planningProbe() {
        return CoreStagePlanningContext.probe();
    }

    private static final class Handle implements CoreStageRegistration {

        private final StageTable table;
        private final String stageId;
        private final CoreStageKind kind;
        private final long generation;
        private final boolean successful;
        private final String reasonKey;

        private volatile boolean active;

        private Handle(StageTable table,
                String stageId,
                CoreStageKind kind,
                long generation,
                boolean successful,
                String reasonKey) {
            this.table = table;
            this.stageId = stageId;
            this.kind = kind;
            this.generation = generation;
            this.successful = successful;
            this.reasonKey = reasonKey;
            this.active = successful;
        }

        @Override
        public boolean successful() {
            return successful;
        }

        @Override
        public @NotNull String stageId() {
            return stageId;
        }

        @Override
        public @NotNull CoreStageKind kind() {
            return kind;
        }

        @Override
        public @NotNull String reasonKey() {
            return reasonKey;
        }

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public void close() {
            if (!active) {
                return;
            }
            active = false;
            table.revoke(stageId, generation);
        }
    }

    public @NotNull Map<CoreStageKind, List<String>> allIds() {
        return Map.of(CoreStageKind.SOURCE, sources.ids(),
                CoreStageKind.GATE, gates.ids(),
                CoreStageKind.ACTION, actions.ids());
    }
}
