package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

abstract class ExperienceStage extends BaseStage {

    ExperienceStage(String id, String description) {
        super(id, "player", description,
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("amount", CoreStageParameterType.INTEGER, "Amount"),
                CoreStageParameter.optional("mode", CoreStageParameterType.STRING, "points",
                        "points or levels"));
    }

    @Override
    public final @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        int amount = Math.max(0, arguments.getInt("amount", 0));
        boolean levels = "levels".equalsIgnoreCase(arguments.getString("mode", "points"));
        apply(target, amount, levels);
        return CoreActionOutcome.success(Map.of(
                "level", target.getLevel(),
                "total_experience", target.getTotalExperience()));
    }

    abstract void apply(Player target, int amount, boolean levels);

    static void setTotalExperience(Player player, int value) {
        if (player == null) {
            return;
        }
        player.setExp(0F);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.giveExp(Math.max(0, value));
    }
}
