package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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

public final class SetAbsorptionStage extends BaseStage {

    public SetAbsorptionStage() {
        super("set_absorption", "entity", "设置实体的伤害吸收生命值。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, "吸收生命值"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (!(context.currentTarget().entityOrNull() instanceof LivingEntity living)) {
            return CoreActionOutcome.skipped("action.stage.common.not_entity");
        }
        float amount = (float) Math.max(0D, arguments.getDouble("amount", 0D));
        living.setAbsorptionAmount(amount);
        if (living.getAbsorptionAmount() < amount) {
            // setAbsorptionAmount 会把值钳制到 MAX_ABSORPTION 属性上（默认 0），
            // 写不进去时先抬高该属性上限再重写；属性缺失则维持原行为
            AttributeInstance ceiling = living.getAttribute(Attribute.MAX_ABSORPTION);
            if (ceiling != null && ceiling.getBaseValue() < amount) {
                ceiling.setBaseValue(amount);
                living.setAbsorptionAmount(amount);
            }
        }
        return CoreActionOutcome.success();
    }
}
