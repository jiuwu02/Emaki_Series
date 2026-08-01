package emaki.jiuwu.craft.cooking.action.v2;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.NutritionOperationResult;
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
 * Adds to, subtracts from, or sets one of the target's nutrition values.
 *
 * <p>The v2 counterpart of {@code NutritionOperationAction}. Two v1 mechanisms are gone:</p>
 * <ul>
 *   <li>the {@code target} argument and its name-or-UUID resolution. That logic lived in
 *       {@code CookingActionExecutionTargets} and is now CoreLib's {@code player_by_name} source, so
 *       {@code player_by_name Steve | cooking_add_nutrition ...} replaces {@code target=Steve};</li>
 *   <li>the hand-built expression variable map. The pipeline renders placeholders into argument text before
 *       arguments are resolved, so {@code amount} declared as {@code EXPRESSION} covers the same ground.</li>
 * </ul>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: writes one player's nutrition record.</p>
 */
public final class NutritionOperationStage implements CoreActionStage {

    /** Which nutrition mutation a stage instance performs. */
    public enum Operation {

        /** Increase the value. */
        ADD("cooking_add_nutrition", "Adds to one of the target's nutrition values."),

        /** Decrease the value. */
        REMOVE("cooking_remove_nutrition", "Removes from one of the target's nutrition values."),

        /** Replace the value. */
        SET("cooking_set_nutrition", "Sets one of the target's nutrition values.");

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

    private final EmakiCookingPlugin plugin;
    private final Operation operation;

    /**
     * Creates a stage.
     *
     * @param plugin owning plugin, source of the nutrition service
     * @param operation which mutation this instance performs
     */
    public NutritionOperationStage(@NotNull EmakiCookingPlugin plugin, @NotNull Operation operation) {
        this.plugin = plugin;
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
        return "cooking";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return List.of(
                CoreStageParameter.required("type", CoreStageParameterType.STRING, "Nutrition type id"),
                CoreStageParameter.required("amount", CoreStageParameterType.EXPRESSION,
                        "Amount, may be an arithmetic expression"));
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
        if (plugin.nutritionService() == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.v2.stage.cooking.service_unavailable");
        }
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        String type = Texts.trim(arguments.getString("type"));
        if (type.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.cooking.type_required");
        }
        UUID targetId = target.getUniqueId();
        double amount = arguments.getExpression("amount", 0D);
        NutritionOperationResult result = switch (operation) {
            case ADD -> plugin.nutritionService().add(targetId, type, amount);
            case REMOVE -> plugin.nutritionService().remove(targetId, type, amount);
            case SET -> plugin.nutritionService().set(targetId, type, amount);
        };
        if (!result.success()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.v2.stage.cooking.operation_failed",
                    Map.of("reason", String.valueOf(result.reason())));
        }
        return CoreActionOutcome.success(Map.of(
                "type", result.typeId(),
                "old_value", result.oldValue(),
                "new_value", result.newValue()));
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
