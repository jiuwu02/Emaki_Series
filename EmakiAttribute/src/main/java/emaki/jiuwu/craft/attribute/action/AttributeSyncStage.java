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

/**
 * Recomputes attribute values, for one target or for everyone online.
 *
 * <p>{@code attribute_refresh} additionally drops the module's caches, which is what makes it the stage to
 * run after editing attribute configuration.</p>
 *
 * <p>This is the one stage in the module whose thread domain depends on its arguments: {@code all=true} walks
 * the online-player list and therefore needs {@code SERVER_GLOBAL}, while {@code all=false} touches a single
 * entity. Both are declared through {@link #executionTarget}, which receives the raw arguments.</p>
 */
public final class AttributeSyncStage implements CoreActionStage {

    /** Which sync variant a stage instance performs. */
    public enum Operation {

        /** Recompute values without dropping caches. Defaults to the single target. */
        SYNC("attribute_sync", "Recomputes attribute values for the target or everyone online.", "false"),

        /** Drop caches, then recompute. Defaults to everyone online. */
        REFRESH("attribute_refresh", "Drops attribute caches and recomputes values.", "true");

        private final String id;
        private final String description;
        private final String allDefault;

        Operation(String id, String description, String allDefault) {
            this.id = id;
            this.description = description;
            this.allDefault = allDefault;
        }

        /** {@return the pipeline stage id} */
        public String id() {
            return id;
        }
    }

    private final AttributeServiceFacade attributeService;
    private final Operation operation;

    /**
     * Creates a stage.
     *
     * @param attributeService the module's service facade
     * @param operation which variant this instance performs
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>{@code OPTIONAL} rather than {@code REQUIRED_ENTITY} because {@code all=true} needs no target at all.
     * The per-target path checks for a player itself.</p>
     */
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

    /**
     * Reads the {@code all} argument during planning.
     *
     * <p>Parsed from raw text because {@link #executionTarget} runs before arguments are resolved. An unresolved
     * placeholder cannot be read here, so it falls back to the operation's default; picking the narrower
     * per-entity domain in that case would be wrong for {@code attribute_refresh}, whose default is global,
     * which is why the default is consulted rather than assuming {@code false}.</p>
     */
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
