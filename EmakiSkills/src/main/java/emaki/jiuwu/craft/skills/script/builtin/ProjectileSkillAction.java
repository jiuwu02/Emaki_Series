package emaki.jiuwu.craft.skills.script.builtin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class ProjectileSkillAction extends AbstractSkillScriptAction {

    public ProjectileSkillAction() {
        super("projectile", "combat", "Launch a custom projectile.",
                ActionParameter.optional("speed", ActionParameterType.DOUBLE, "1.5", "Blocks per tick"),
                ActionParameter.optional("gravity", ActionParameterType.DOUBLE, "0.05", "Gravity per tick"),
                ActionParameter.optional("lifetime", ActionParameterType.INTEGER, "60", "Max lifetime in ticks"),
                ActionParameter.optional("hit_radius", ActionParameterType.DOUBLE, "0.5", "Hit detection radius"),
                ActionParameter.optional("pierce", ActionParameterType.INTEGER, "0", "Number of entities to pierce through"),
                ActionParameter.optional("homing", ActionParameterType.STRING, "false", "Enable homing toward target"),
                ActionParameter.optional("homing_strength", ActionParameterType.DOUBLE, "0.1", "Homing turn strength"),
                ActionParameter.optional("particle", ActionParameterType.STRING, "FLAME", "Trail particle type"),
                ActionParameter.optional("damage", ActionParameterType.DOUBLE, "0", "Damage on hit (0 = no damage)"),
                ActionParameter.optional("direction", ActionParameterType.STRING, "look", "Initial direction (look/target)"));
    }

    @Override
    public CompletableFuture<ActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        Player caster = context.caster();
        if (caster == null || !caster.isOnline()) {
            return completed(ActionResult.ok());
        }

        double speed = doubleArg(arguments, "speed", 1.5D);
        double gravity = doubleArg(arguments, "gravity", 0.05D);
        int lifetime = intArg(arguments, "lifetime", 60);
        double hitRadius = doubleArg(arguments, "hit_radius", 0.5D);
        int pierce = intArg(arguments, "pierce", 0);
        boolean homing = Boolean.parseBoolean(arg(arguments, "homing", "false"));
        double homingStrength = doubleArg(arguments, "homing_strength", 0.1D);
        String particleName = arg(arguments, "particle", "FLAME").toUpperCase();
        double damage = doubleArg(arguments, "damage", 0D);
        String directionMode = arg(arguments, "direction", "look").toLowerCase();

        Particle particle = parseParticle(particleName);
        Location origin = caster.getEyeLocation();
        Vector direction = resolveDirection(context, directionMode, origin);
        direction.normalize().multiply(speed);

        double hitRadiusSq = hitRadius * hitRadius;
        List<Entity> alreadyHit = new ArrayList<>();

        new BukkitRunnable() {
            Location current = origin.clone();
            Vector velocity = direction.clone();
            int ticksLived = 0;
            int pierceRemaining = pierce;

            @Override
            public void run() {
                if (ticksLived >= lifetime || !caster.isOnline()) {
                    cancel();
                    return;
                }
                ticksLived++;

                velocity.setY(velocity.getY() - gravity);

                if (homing && context.targetEntity() != null && !context.targetEntity().isDead()) {
                    Vector toTarget = context.targetEntity().getLocation().add(0, 1, 0)
                            .toVector().subtract(current.toVector()).normalize();
                    velocity.add(toTarget.multiply(homingStrength)).normalize().multiply(speed);
                }

                current.add(velocity);
                World world = current.getWorld();
                if (world == null) {
                    cancel();
                    return;
                }

                if (particle != null) {
                    world.spawnParticle(particle, current, 1, 0, 0, 0, 0);
                }

                if (current.getBlock().getType().isSolid()) {
                    cancel();
                    return;
                }

                Collection<Entity> nearby = world.getNearbyEntities(current, hitRadius, hitRadius, hitRadius);
                for (Entity entity : nearby) {
                    if (!(entity instanceof LivingEntity living)) {
                        continue;
                    }
                    if (entity.equals(caster) || alreadyHit.contains(entity)) {
                        continue;
                    }
                    if (entity.getLocation().distanceSquared(current) > hitRadiusSq * 4) {
                        continue;
                    }

                    alreadyHit.add(entity);
                    context.setTarget(living);
                    context.putVariable("projectile_hit", "1");
                    context.putSharedValue("projectile_hit_entity", living);

                    if (damage > 0D) {
                        living.damage(damage, caster);
                    }

                    if (pierceRemaining <= 0) {
                        cancel();
                        return;
                    }
                    pierceRemaining--;
                }
            }
        }.runTaskTimer(context.plugin(), 0L, 1L);

        return completed(ActionResult.ok());
    }

    private Vector resolveDirection(SkillScriptContext context, String mode, Location origin) {
        if ("target".equals(mode) && context.targetEntity() != null) {
            return context.targetEntity().getLocation().add(0, 1, 0)
                    .toVector().subtract(origin.toVector());
        }
        return origin.getDirection();
    }

    private Particle parseParticle(String name) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException _) {
            return Particle.FLAME;
        }
    }
}
