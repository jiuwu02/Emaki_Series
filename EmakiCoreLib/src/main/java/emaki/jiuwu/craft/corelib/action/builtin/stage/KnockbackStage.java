package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

public final class KnockbackStage extends BaseStage {

    public KnockbackStage() {
        super("knockback", "movement", "把目标从施法者方向推开。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.optional("strength", CoreStageParameterType.DOUBLE, "1", "击退力度"),
                CoreStageParameter.optional("vertical", CoreStageParameterType.DOUBLE, "0.4",
                        "垂直上升速度"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (!(context.currentTarget().entityOrNull() instanceof LivingEntity target)) {
            return CoreActionOutcome.skipped("action.stage.common.not_entity");
        }
        Entity caster = context.caster().entityOrNull();
        if (caster == null) {
            return CoreActionOutcome.skipped("action.stage.knockback.no_caster");
        }
        Location casterLocation = caster.getLocation();
        Location targetLocation = target.getLocation();
        if (casterLocation.getWorld() == null
                || !casterLocation.getWorld().equals(targetLocation.getWorld())) {
            return CoreActionOutcome.skipped("action.stage.knockback.world_mismatch");
        }
        Vector direction = targetLocation.toVector().subtract(casterLocation.toVector());
        double strength = Math.max(0D, arguments.getDouble("strength", 1D));
        double vertical = arguments.getDouble("vertical", 0.4D);
        if (direction.lengthSquared() < 1.0E-4D) {
            direction = casterLocation.getDirection();
        }
        direction.normalize().multiply(strength);
        direction.setY(vertical);
        target.setVelocity(direction);
        return CoreActionOutcome.success();
    }
}
