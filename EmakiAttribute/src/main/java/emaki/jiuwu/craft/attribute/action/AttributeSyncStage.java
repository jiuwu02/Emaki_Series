package emaki.jiuwu.craft.attribute.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

public final class AttributeSyncStage implements CoreActionStage {

    public enum Operation {

        SYNC("attribute_sync", "Recomputes attribute values for the target or everyone online.", "false"),

        REFRESH("attribute_refresh", "Drops attribute caches and recomputes values.", "true");

        private final String id;
        private final String description;
        private final String allDefault;

        Operation(String id, String description, String allDefault) {
            this.id = id;
            this.description = description;
            this.allDefault = allDefault;
        }

        public String id() {
            return id;
        }
    }

    private final AttributeServiceFacade attributeService;
    private final Operation operation;

    public AttributeSyncStage(@NotNull AttributeServiceFacade attributeService,
            @NotNull Operation operation) {
        this.attributeService = attributeService;
        this.operation = operation;
    }

    @Override
    public @NotNull String id() {
        return operation.id;
    }

    @Override
    public @NotNull String description() {
        return operation.description;
    }

    @Override
    public @NotNull String category() {
        return "attribute";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return List.of(CoreStageParameter.optional("all", CoreStageParameterType.BOOLEAN,
                operation.allDefault, "Apply to every online player instead of the target"));
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.OPTIONAL;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return appliesToEveryone(context.argument("all"))
                ? CoreActionExecutionTarget.global()
                : CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (attributeService == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.attribute.service_unavailable");
        }
        boolean all = arguments.getBoolean("all", Boolean.parseBoolean(operation.allDefault));
        if (operation == Operation.REFRESH) {
            attributeService.refreshCaches();
        }
        if (all) {
            attributeService.resyncAllPlayers();
            return CoreActionOutcome.success(Map.of("all", true));
        }
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        attributeService.syncPlayer(target, ResourceSyncReason.MANUAL, null);
        return CoreActionOutcome.success(Map.of("all", false, "player", target.getName()));
    }

    private boolean appliesToEveryone(String rawAll) {
        String value = rawAll == null ? "" : rawAll.trim();
        if (value.isEmpty() || value.indexOf('%') >= 0) {
            return Boolean.parseBoolean(operation.allDefault);
        }
        return Boolean.parseBoolean(value);
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
