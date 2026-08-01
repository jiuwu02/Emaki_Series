package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;

/**
 * Removes one potion effect from the target.
 *
 * <p>Domain {@code CONTEXT_ENTITY}: writes one entity's effect list.</p>
 */
public final class RemovePotionEffectStage extends BaseStage {

    public RemovePotionEffectStage() {
        super("remove_potion_effect", "entity", "Removes one potion effect from the target.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("type", CoreStageParameterType.STRING, "Effect type"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        LivingEntity target = StageSupport.livingEntity(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_living_entity");
        }
        PotionEffectType type = PotionEffects.resolve(arguments.getString("type"));
        if (type == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.potion.unknown_effect", Map.of("type", arguments.getString("type")));
        }
        if (!target.hasPotionEffect(type)) {
            return CoreActionOutcome.skipped("action.v2.stage.potion.effect_absent");
        }
        target.removePotionEffect(type);
        return CoreActionOutcome.success(Map.of("type", type.getKey().getKey()));
    }
}
