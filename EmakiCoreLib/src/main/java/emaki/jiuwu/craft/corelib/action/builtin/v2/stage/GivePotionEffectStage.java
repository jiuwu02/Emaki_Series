package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
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
 * Applies a potion effect to the target.
 *
 * <p>{@code level} is one-based as in v1, so {@code level=1} is amplifier 0 and matches what the game shows.
 * {@code duration} is now {@code DURATION} rather than {@code TIME}; the parser is the same.</p>
 *
 * <p>Widened to {@code LivingEntity}, which is where {@code addPotionEffect} is defined.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: writes one entity's effect list.</p>
 */
public final class GivePotionEffectStage extends BaseStage {

    public GivePotionEffectStage() {
        super("give_potion_effect", "entity", "Applies a potion effect to the target.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("type", CoreStageParameterType.STRING, "Effect type"),
                CoreStageParameter.required("level", CoreStageParameterType.INTEGER, "Effect level, one-based"),
                CoreStageParameter.required("duration", CoreStageParameterType.DURATION, "Effect duration"),
                CoreStageParameter.optional("ambient", CoreStageParameterType.BOOLEAN, "false", "Ambient"),
                CoreStageParameter.optional("particles", CoreStageParameterType.BOOLEAN, "true", "Particles"),
                CoreStageParameter.optional("icon", CoreStageParameterType.BOOLEAN, "true", "HUD icon"));
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
