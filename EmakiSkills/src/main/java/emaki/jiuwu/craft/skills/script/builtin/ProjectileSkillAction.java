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
import org.bukkit.util.Vector;

import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.skills.api.SkillActionParameter;
import emaki.jiuwu.craft.skills.api.SkillActionParameterType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class ProjectileSkillAction extends AbstractSkillScriptAction {

    public ProjectileSkillAction() {
        super("projectile", "combat", "Launch a custom projectile.",
                SkillActionParameter.optional("speed", SkillActionParameterType.DOUBLE, "1.5", "Blocks per tick"),
                SkillActionParameter.optional("gravity", SkillActionParameterType.DOUBLE, "0.05", "Gravity per tick"),
                SkillActionParameter.optional("lifetime", SkillActionParameterType.INTEGER, "60", "Max lifetime in ticks"),
                SkillActionParameter.optional("hit_radius", SkillActionParameterType.DOUBLE, "0.5", "Hit detection radius"),
                SkillActionParameter.optional("pierce", SkillActionParameterType.INTEGER, "0", "Number of entities to pierce through"),
                SkillActionParameter.optional("homing", SkillActionParameterType.STRING, "false", "Enable homing toward target"),
                SkillActionParameter.optional("homing_strength", SkillActionParameterType.DOUBLE, "0.1", "Homing turn strength"),
                SkillActionParameter.optional("particle", SkillActionParameterType.STRING, "FLAME", "Trail particle type"),
                SkillActionParameter.optional("damage", SkillActionParameterType.DOUBLE, "0", "Damage on hit (0 = no damage)"),
                SkillActionParameter.optional("direction", SkillActionParameterType.STRING, "look", "Initial direction (look/target)"));
    }

    @Override
    public CompletableFuture<SkillActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        Player caster = context.caster();
        if (caster == null || !caster.isOnline()) {
            return completed(SkillActionResult.ok());
        }

        double speed = doubleArg(arguments, "speed", 1.5D);
        double gravity = doubleArg(arguments, "gravity", 0.05D);
        int lifetime = intArg(arguments, "lifetime", 60);
        double hitRadius = doubleArg(arguments, "hit_radius", 0.5D);
        int pierce = intArg(arguments, "pierce", 0);
        boolean homing = Boolean.parseBoolean(arg(arguments, "homing", "false"));
        double homingStrength = doubleArg(arguments, "homing_strength", 0.1D);
        String particleName = arg(arguments, "particle", "FLAME").toUpperCase(java.util.Locale.ROOT);
        double damage = doubleArg(arguments, "damage", 0D);
        String directionMode = arg(arguments, "direction", "look").toLowerCase(java.util.Locale.ROOT);

        Particle particle = parseParticle(particleName);
        Location origin = caster.getEyeLocation();
        Vector direction = resolveDirection(context, directionMode, origin);
        direction.normalize().multiply(speed);

        double hitRadiusSq = hitRadius * hitRadius;
        List<Entity> alreadyHit = new ArrayList<>();

        class ProjectileFlight {
            private Location current = origin.clone();
            private final Vector velocity = direction.clone();
            private int ticksLived;
            private int pierceRemaining = pierce;

            private void scheduleNextTick() {
                if (ticksLived >= lifetime || !caster.isOnline()) {
                    return;
                }
                ticksLived++;
                velocity.setY(velocity.getY() - gravity);

                Entity homingTarget = context.targetEntity();
                if (homing && homingTarget != null && !homingTarget.isDead()) {
                    Vector toTarget = homingTarget.getLocation().add(0, 1, 0)
                            .toVector().subtract(current.toVector()).normalize();
                    velocity.add(toTarget.multiply(homingStrength)).normalize().multiply(speed);
                }

                current.add(velocity);
                FoliaSchedulerAdapter.runAtLocationLater(
                        context.plugin(),
                        current.clone(),
                        this::tick,
                        1L);
            }

            private void tick() {
                if (!caster.isOnline()) {
                    return;
                }
                World world = current.getWorld();
                if (world == null) {
                    return;
                }

                if (particle != null) {
                    world.spawnParticle(particle, current, 1, 0, 0, 0, 0);
                }

                if (current.getBlock().getType().isSolid()) {
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
                        return;
                    }
                    pierceRemaining--;
                }
                scheduleNextTick();
            }
        }

        new ProjectileFlight().scheduleNextTick();

        return completed(SkillActionResult.ok());
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
