package emaki.jiuwu.craft.attribute.action.v2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.ResourceDefinition;
import emaki.jiuwu.craft.attribute.model.ResourceState;
import emaki.jiuwu.craft.attribute.model.ResourceSyncReason;
import emaki.jiuwu.craft.attribute.service.AttributeServiceFacade;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Adds to, sets, or subtracts from one of the target's resources.
 *
 * <p>The v2 counterpart of {@code ResourceModifyAction} and {@code ResourceConsumeAction}. Those were two
 * classes in v1 although {@code consume} and {@code remove} computed the same value; they stay as separate
 * stage ids here because configuration refers to them by name, but they share one implementation.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: reads and writes one player's resource state.</p>
 */
public final class ResourceModifyStage implements CoreActionStage {

    /** Which resource mutation a stage instance performs. */
    public enum Operation {

        /** Increase the current value. */
        ADD("attribute_resource_add", "Adds to one of the target's resources."),

        /** Replace the current value. */
        SET("attribute_resource_set", "Sets one of the target's resources."),

        /** Decrease the current value, floored at zero. */
        REMOVE("attribute_resource_remove", "Removes from one of the target's resources."),

        /** Spend from the current value. Same arithmetic as {@link #REMOVE}, kept as its own id. */
        CONSUME("attribute_resource_consume", "Consumes one of the target's resources.");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
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
     * @param operation which mutation this instance performs
     */
    public ResourceModifyStage(@NotNull AttributeServiceFacade attributeService,
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
        return List.of(
                CoreStageParameter.required("resource", CoreStageParameterType.STRING, "Resource id"),
                CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, "Amount"));
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.REQUIRED_ENTITY;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (attributeService == null || attributeService.resourceDefinitions() == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.v2.stage.attribute.service_unavailable");
        }
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        String resourceId = Texts.normalizeId(arguments.getString("resource"));
        ResourceDefinition definition = attributeService.resourceDefinitions().get(resourceId);
        if (definition == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.attribute.unknown_resource", Map.of("resource", resourceId));
        }
        double amount = arguments.getDouble("amount", 0D);
        AttributeSnapshot snapshot = attributeService.collectCombatSnapshot(target);
        double oldValue = currentValue(target, definition, resourceId, snapshot);
        double requested = switch (operation) {
            case ADD -> oldValue + amount;
            case SET -> amount;
            case REMOVE, CONSUME -> Math.max(0D, oldValue - amount);
        };
        ResourceState result = attributeService.syncResource(
                target, definition, snapshot, ResourceSyncReason.MANUAL, requested);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", resourceId);
        data.put("operation", operation.name().toLowerCase(Locale.ROOT));
        data.put("amount", amount);
        data.put("old_value", oldValue);
        data.put("new_value", result == null ? requested : result.currentValue());
        data.put("current_max", result == null ? 0D : result.currentMax());
        return CoreActionOutcome.success(Map.copyOf(data));
    }

    /**
     * Reads the resource's current value, initialising it when the player has none yet.
     *
     * <p>The sync-with-null call is how v1 primed a resource that had never been written: without it the
     * baseline would be zero and an {@code add} would start from the wrong number.</p>
     */
    private double currentValue(Player target,
            ResourceDefinition definition,
            String resourceId,
            AttributeSnapshot snapshot) {
        ResourceState current = attributeService.readResourceState(target, resourceId);
        ResourceState synced = current == null
                ? attributeService.syncResource(target, definition, snapshot, ResourceSyncReason.MANUAL, null)
                : current;
        return synced == null ? 0D : synced.currentValue();
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
