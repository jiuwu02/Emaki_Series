package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

public final class ClearPotionEffectsStage extends BaseStage {

    public ClearPotionEffectsStage() {
        super("clear_potion_effects", "entity", "Removes every potion effect from the target.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY);
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        LivingEntity target = StageSupport.livingEntity(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_living_entity");
        }

        List<PotionEffect> active = List.copyOf(target.getActivePotionEffects());
        if (active.isEmpty()) {
            return CoreActionOutcome.skipped("action.stage.potion.none_active");
        }
        active.forEach(effect -> target.removePotionEffect(effect.getType()));
        return CoreActionOutcome.success(Map.of("removed", active.size()));
    }
}
