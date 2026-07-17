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
import emaki.jiuwu.craft.skills.api.SkillScriptAction;
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
        return executeFlight(context, arguments, new SkillScriptAction.CancellationToken());
    }

    @Override
    public CompletableFuture<SkillActionResult> executeAsync(SkillScriptContext context,
            Map<String, String> arguments,
            SkillScriptAction.CancellationToken cancellationToken) {
        return executeFlight(context, arguments,
                cancellationToken == null ? new SkillScriptAction.CancellationToken() : cancellationToken);
    }

    private CompletableFuture<SkillActionResult> executeFlight(SkillScriptContext context,
            Map<String, String> arguments,
            SkillScriptAction.CancellationToken cancellationToken) {
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
        Particle particle = parseParticle(arg(arguments, "particle", "FLAME")
                .toUpperCase(java.util.Locale.ROOT));
        double damage = doubleArg(arguments, "damage", 0D);
        String directionMode = arg(arguments, "direction", "look").toLowerCase(java.util.Locale.ROOT);
        Location origin = caster.getEyeLocation();
        Entity target = context.targetEntity();

        CompletableFuture<Location> targetLocation = target == null
                ? CompletableFuture.completedFuture(null)
                : callOnEntity(context, target, () -> target.getLocation().add(0, 1, 0));
        CompletableFuture<SkillActionResult> flight = targetLocation.thenCompose(aimLocation -> {
            Vector direction = resolveDirection(directionMode, origin, aimLocation)
                    .normalize().multiply(speed);
            List<Entity> alreadyHit = new ArrayList<>();
            CompletableFuture<SkillActionResult> completion = new CompletableFuture<>();

            class ProjectileFlight {
                private Location current = origin.clone();
                private final Vector velocity = direction.clone();
                private int ticksLived;
                private int pierceRemaining = pierce;

                private void finish() {
                    completion.complete(SkillActionResult.ok());
                }

                private void scheduleNextTick() {
                    if (cancellationToken.isCancelled()) {
                        finish();
                        return;
                    }
                    if (completion.isDone() || ticksLived >= lifetime) {
                        finish();
                        return;
                    }
                    ticksLived++;
                    velocity.setY(velocity.getY() - gravity);
                    if (homing && aimLocation != null && aimLocation.getWorld() == current.getWorld()) {
                        Vector toTarget = aimLocation.toVector().subtract(current.toVector());
                        if (toTarget.lengthSquared() > 0.0001D) {
                            velocity.add(toTarget.normalize().multiply(homingStrength))
                                    .normalize().multiply(speed);
                        }
                    }
                    current.add(velocity);
                    try {
                        var scheduled = FoliaSchedulerAdapter.runAtLocationLater(
                                context.plugin(), current.clone(), this::tick, 1L);
                        if (scheduled == null) {
                            completion.completeExceptionally(new IllegalStateException(
                                    "Projectile flight scheduling was rejected."));
                        }
                    } catch (Throwable throwable) {
                        completion.completeExceptionally(throwable);
                    }
                }

                private void tick() {
                    if (cancellationToken.isCancelled()) {
                        finish();
                        return;
                    }
                    World world = current.getWorld();
                    if (world == null || current.getBlock().getType().isSolid()) {
                        finish();
                        return;
                    }
                    if (particle != null) {
                        world.spawnParticle(particle, current, 1, 0, 0, 0, 0);
                    }
                    Collection<Entity> nearby = world.getNearbyEntities(
                            current, hitRadius, hitRadius, hitRadius);
                    List<CompletableFuture<?>> hitTasks = new ArrayList<>();
                    boolean terminalHit = false;
                    for (Entity entity : nearby) {
                        if (!(entity instanceof LivingEntity living)
                                || entity.equals(caster)
                                || alreadyHit.contains(entity)) {
                            continue;
                        }
                        alreadyHit.add(entity);
                        hitTasks.add(callOnEntity(context, living, () -> {
                            if (cancellationToken.isCancelled()) {
                                return null;
                            }
                            if (damage > 0D) {
                                living.damage(damage, caster);
                            }
                            context.setTarget(living);
                            context.putVariable("projectile_hit", "1");
                            context.putSharedValue("projectile_hit_entity", living);
                            return null;
                        }));
                        if (pierceRemaining <= 0) {
                            terminalHit = true;
                            break;
                        }
                        pierceRemaining--;
                    }
                    boolean stop = terminalHit;
                    CompletableFuture.allOf(hitTasks.toArray(CompletableFuture[]::new))
                            .whenComplete((_, throwable) -> {
                                if (throwable != null) {
                                    completion.completeExceptionally(throwable);
                                } else if (stop) {
                                    finish();
                                } else {
                                    scheduleNextTick();
                                }
                            });
                }
            }

            new ProjectileFlight().scheduleNextTick();
            return completion;
        });
        flight.whenComplete((_, _) -> {
            if (flight.isCancelled()) {
                cancellationToken.cancel();
            }
        });
        return flight;
    }

    private Vector resolveDirection(String mode, Location origin, Location targetLocation) {
        if ("target".equals(mode) && targetLocation != null
                && targetLocation.getWorld() == origin.getWorld()) {
            return targetLocation.toVector().subtract(origin.toVector());
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
