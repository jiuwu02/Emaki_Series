package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;

/**
 * Restores food and optionally saturation on the target.
 *
 * <p>Still requires a {@code Player}: hunger and saturation exist only on players, so a non-player target is
 * {@code Skipped} rather than an error.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: reads and writes one player's food state.</p>
 */
public final class FeedStage extends BaseStage {

    public FeedStage() {
        super("feed", "entity", "Restores food and optional saturation on the target.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.optional("amount", CoreStageParameterType.INTEGER, "20",
                        "Food points to restore"),
                CoreStageParameter.optional("saturation", CoreStageParameterType.DOUBLE, "0",
                        "Saturation to restore"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        int amount = Math.max(0, arguments.getInt("amount", 20));
        float saturation = (float) Math.max(0D, arguments.getDouble("saturation", 0D));
        int beforeFood = target.getFoodLevel();
        float beforeSaturation = target.getSaturation();
        target.setFoodLevel(Math.min(20, beforeFood + amount));
        if (saturation > 0F) {
            target.setSaturation(Math.min(20F, beforeSaturation + saturation));
        }
        return CoreActionOutcome.success(Map.of(
                "food_before", beforeFood,
                "food_after", target.getFoodLevel(),
                "saturation_before", beforeSaturation,
                "saturation_after", target.getSaturation()));
    }
}
