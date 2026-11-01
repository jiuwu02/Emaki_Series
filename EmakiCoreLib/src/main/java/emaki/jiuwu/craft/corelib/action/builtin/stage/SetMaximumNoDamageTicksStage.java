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

public final class SetMaximumNoDamageTicksStage extends BaseStage {

    public SetMaximumNoDamageTicksStage() {
        super("set_max_no_damage_ticks", "entity", "设置实体无敌帧上限。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("ticks", CoreStageParameterType.INTEGER, "无敌帧上限"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (!(context.currentTarget().entityOrNull() instanceof LivingEntity living)) {
            return CoreActionOutcome.skipped("action.stage.common.not_entity");
        }
        living.setMaximumNoDamageTicks(Math.max(0, arguments.getInt("ticks", 0)));
        return CoreActionOutcome.success();
    }
}
