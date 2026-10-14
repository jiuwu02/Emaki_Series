package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

public final class SetCompassTargetStage extends BaseStage {

    public SetCompassTargetStage() {
        super("set_compass_target", "player", "把玩家指南针指向管道原点。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY);
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (!(context.currentTarget().entityOrNull() instanceof Player player)) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        Location origin;
        try {
            origin = context.origin();
        } catch (IllegalStateException exception) {
            return CoreActionOutcome.skipped("action.stage.set_compass_target.no_origin");
        }
        if (origin == null || origin.getWorld() == null) {
            return CoreActionOutcome.skipped("action.stage.set_compass_target.no_origin");
        }
        player.setCompassTarget(origin);
        return CoreActionOutcome.success();
    }
}
