package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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

public final class GivePotionEffectStage extends BaseStage {

    public GivePotionEffectStage() {
        super("give_potion_effect", "entity", "为目标施加一个药水效果。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("type", CoreStageParameterType.STRING, "效果类型"),
                CoreStageParameter.required("level", CoreStageParameterType.INTEGER, "效果等级，从 1 开始"),
                CoreStageParameter.required("duration", CoreStageParameterType.DURATION, "效果持续时间"),
                CoreStageParameter.optional("ambient", CoreStageParameterType.BOOLEAN, "false", "环境效果"),
                CoreStageParameter.optional("particles", CoreStageParameterType.BOOLEAN, "true", "粒子效果"),
                CoreStageParameter.optional("icon", CoreStageParameterType.BOOLEAN, "true", "HUD 图标"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        LivingEntity target = StageSupport.livingEntity(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_living_entity");
        }
        PotionEffectType type = PotionEffects.resolve(arguments.getString("type"));
        if (type == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.potion.unknown_effect", Map.of("type", arguments.getString("type")));
        }
        int amplifier = Math.max(0, arguments.getInt("level", 1) - 1);
        int duration = (int) Math.min(Integer.MAX_VALUE, arguments.getDurationTicks("duration", 0L));
        target.addPotionEffect(new PotionEffect(type, duration, amplifier,
                arguments.getBoolean("ambient", false),
                arguments.getBoolean("particles", true),
                arguments.getBoolean("icon", true)));
        return CoreActionOutcome.success(Map.of("type", type.getKey().getKey(),
                "amplifier", amplifier, "duration_ticks", duration));
    }
}
