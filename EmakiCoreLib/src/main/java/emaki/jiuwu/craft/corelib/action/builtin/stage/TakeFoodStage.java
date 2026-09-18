package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger;
import emaki.jiuwu.craft.corelib.debug.ActionAuditLogger.OperationType;

public final class TakeFoodStage extends BaseStage {

    private final ActionAuditLogger auditLogger;

    public TakeFoodStage(ActionAuditLogger auditLogger) {
        super("take_food", "entity", "Removes food and optional saturation from the target.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("amount", CoreStageParameterType.INTEGER, "Food points to remove"),
                CoreStageParameter.optional("saturation", CoreStageParameterType.DOUBLE, "0",
                        "Saturation to remove"));
        this.auditLogger = auditLogger;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        int amount = arguments.getInt("amount", 0);
        if (amount <= 0) {
            Map<String, Object> args = Map.of("amount", amount);
            auditLogger.logFailure(id(), target, OperationType.DECREASE, amount,
                    "action.stage.common.invalid_positive_amount", args, context);
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.common.invalid_positive_amount", args);
        }
        double saturationDrain = Math.max(0D, arguments.getDouble("saturation", 0D));

        int beforeFood = target.getFoodLevel();
        if (beforeFood < amount) {
            Map<String, Object> args = Map.of("required", amount, "current", beforeFood);
            auditLogger.logFailure(id(), target, OperationType.DECREASE, amount,
                    "action.stage.food.insufficient", args, context);
            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.stage.food.insufficient", args);
        }
        float beforeSaturation = target.getSaturation();
        target.setFoodLevel(beforeFood - amount);
        if (saturationDrain > 0D) {
            float newSat = Math.max(0F, beforeSaturation - (float) saturationDrain);
            target.setSaturation(newSat);
        }
        auditLogger.logSuccess(id(), target, OperationType.DECREASE, beforeFood, target.getFoodLevel(),
                amount, context);
        return CoreActionOutcome.success(Map.of(
                "food_before", beforeFood,
                "food_after", target.getFoodLevel(),
                "saturation_before", beforeSaturation,
                "saturation_after", target.getSaturation()));
    }
}