package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

public final class SetWalkSpeedStage extends BaseStage {

    public SetWalkSpeedStage() {
        super("set_walk_speed", "player", "设置玩家行走速度。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("speed", CoreStageParameterType.DOUBLE,
                        "行走速度，范围 -1.0 至 1.0"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (!(context.currentTarget().entityOrNull() instanceof Player player)) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        player.setWalkSpeed((float) Math.max(-1D, Math.min(1D, arguments.getDouble("speed", 0D))));
        return CoreActionOutcome.success();
    }
}
