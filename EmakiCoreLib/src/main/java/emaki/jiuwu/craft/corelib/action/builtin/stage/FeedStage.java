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

public final class FeedStage extends BaseStage {

    private final ActionAuditLogger auditLogger;

    public FeedStage(ActionAuditLogger auditLogger) {
        super("feed", "entity", "恢复目标的食物值，并可恢复饱和度。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.optional("amount", CoreStageParameterType.INTEGER, "20",
                        "要恢复的食物点数"),
                CoreStageParameter.optional("saturation", CoreStageParameterType.DOUBLE, "0",
                        "要恢复的饱和度"));
        this.auditLogger = auditLogger;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        int amount = arguments.getInt("amount", 20);
        if (amount < 0) {
            Map<String, Object> args = Map.of("amount", amount);
            auditLogger.logFailure(id(), target, OperationType.INCREASE, amount,
                    "action.stage.common.invalid_amount", args, context);
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.common.invalid_amount", args);
        }
        float saturation = (float) Math.max(0D, arguments.getDouble("saturation", 0D));
        int beforeFood = target.getFoodLevel();
        float beforeSaturation = target.getSaturation();
        int afterFood = Math.min(20, beforeFood + amount);
        target.setFoodLevel(afterFood);
        if (saturation > 0F) {
            target.setSaturation(Math.min(20F, beforeSaturation + saturation));
        }
        auditLogger.logSuccess(id(), target, OperationType.INCREASE, beforeFood, target.getFoodLevel(),
                amount, context);
        return CoreActionOutcome.success(Map.of(
                "food_before", beforeFood,
                "food_after", target.getFoodLevel(),
                "saturation_before", beforeSaturation,
                "saturation_after", target.getSaturation()));
    }
}