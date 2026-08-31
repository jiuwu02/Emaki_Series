package emaki.jiuwu.craft.cooking.action;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.model.NutritionOperationResult;
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
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class NutritionOperationStage implements CoreActionStage {

    public enum Operation {

        ADD("cooking_add_nutrition", "Adds to one of the target's nutrition values."),

        REMOVE("cooking_remove_nutrition", "Removes from one of the target's nutrition values."),

        SET("cooking_set_nutrition", "Sets one of the target's nutrition values.");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String id() {
            return id;
        }
    }

    private final EmakiCookingPlugin plugin;
    private final Operation operation;

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
                    "action.stage.cooking.service_unavailable");
        }
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        String type = Texts.trim(arguments.getString("type"));
        if (type.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.cooking.type_required");
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
                    "action.stage.cooking.operation_failed",
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
