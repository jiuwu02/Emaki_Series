package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

public final class SetRemoveWhenFarAwayStage extends BaseStage {

    public SetRemoveWhenFarAwayStage() {
        super("set_remove_when_far_away", "meta", "控制生物距离过远时是否被移除。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("remove", CoreStageParameterType.BOOLEAN, "是否移除"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (!(context.currentTarget().entityOrNull() instanceof LivingEntity living)) {
            return CoreActionOutcome.skipped("action.stage.common.not_entity");
        }
        living.setRemoveWhenFarAway(arguments.getBoolean("remove", true));
        return CoreActionOutcome.success();
    }
}
