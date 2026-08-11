package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

/**
 * Lowers the target's health by a flat amount.
 *
 * <p>Sets health directly, exactly as v1 did, rather than calling {@code LivingEntity#damage}. That keeps
 * the behaviour identical across the migration: no damage event, no armour or resistance reduction, no
 * knockback and no hurt animation.</p>
 *
 * <p>Omitting the source means {@code self}, so a bare {@code damage amount=5} hurts the caster — the same
 * result the v1 action produced with no target argument.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: reads and writes one entity's health.</p>
 */
public final class DamageStage extends BaseStage {

    public DamageStage() {
        super("damage", "entity", "Lowers the target's health by a flat amount.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("amount", CoreStageParameterType.DOUBLE, "Health to remove"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        LivingEntity target = StageSupport.livingEntity(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_living_entity");
        }
        double amount = arguments.getDouble("amount", 0D);
        double before = target.getHealth();
        target.setHealth(Math.max(0D, before - amount));
        return CoreActionOutcome.success(Map.of("health_before", before, "health_after", target.getHealth()));
    }
}
