package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
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
            return CoreActionOutcome.skipped("action.stage.common.not_living_entity");
        }
        PotionEffectType type = PotionEffects.resolve(arguments.getString("type"));
        if (type == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.potion.unknown_effect", Map.of("type", arguments.getString("type")));
        }
        if (!target.hasPotionEffect(type)) {
            return CoreActionOutcome.skipped("action.stage.potion.effect_absent");
        }
        target.removePotionEffect(type);
        return CoreActionOutcome.success(Map.of("type", type.getKey().getKey()));
    }
}
