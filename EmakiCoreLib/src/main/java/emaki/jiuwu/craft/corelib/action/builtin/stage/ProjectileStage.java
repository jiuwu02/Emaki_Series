package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.ValueParsers;
import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionKeys;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreCancellationToken;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Launches a self-driven projectile from the caster's eyes.
 *
 * <p>Ported from the Skills-side {@code projectile} action. Two behaviours were deliberately narrowed and
 * neither is faked back:</p>
 *
 * <ol>
 *   <li><b>The hit entity does not re-enter the target flow.</b> v1 wrote {@code context.setTarget(living)},
 *       a {@code projectile_hit} variable and a shared-state entry, so later lines could act on whatever the
 *       projectile struck. {@link CoreStageContext} is read-only and only a gate's {@code Passed.data} is
 *       written back, so this stage can report {@code hit_count} in its success data but cannot change what
 *       follows it. Configuration that needs the impact point has to pick its own targets.</li>
 *   <li><b>It does not wait for the flight to finish.</b> {@code execute} returns a synchronous outcome, so
 *       the launch returns {@code Success} immediately and the flight continues on the region scheduler.
 *       Lines after this one run at once rather than after impact; use {@code after} when a later line must
 *       be delayed.</li>
 * </ol>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: the launch reads the caster's eye location and direction. Each flight tick
 * then re-schedules itself onto the region that owns its current position, which is what keeps the moving
 * projectile legal on Folia.</p>
 */
public final class ProjectileStage extends BaseStage {

    private final ExecutionDispatcher executionDispatcher;
    private final Plugin owner;

    /**
     * Creates the stage.
     *
     * @param executionDispatcher scheduler bridge used to drive the flight, may be {@code null} in tests
     * @param owner plugin the flight tasks belong to, may be {@code null} in tests
     */
    public ProjectileStage(ExecutionDispatcher executionDispatcher, Plugin owner) {
        super("projectile", "combat", "Launches a self-driven projectile from the caster.",
                CoreTargetRequirement.OPTIONAL, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.optional("speed", CoreStageParameterType.DOUBLE, "1.5",
                        "Blocks per tick"),
                CoreStageParameter.optional("gravity", CoreStageParameterType.DOUBLE, "0.05",
                        "Downward pull per tick"),
                CoreStageParameter.optional("lifetime", CoreStageParameterType.INTEGER, "60",
                        "Maximum lifetime in ticks"),
                CoreStageParameter.optional("hit_radius", CoreStageParameterType.DOUBLE, "0.5",
                        "Hit detection radius"),
                CoreStageParameter.optional("pierce", CoreStageParameterType.INTEGER, "0",
                        "How many extra entities the projectile passes through"),
                CoreStageParameter.optional("homing", CoreStageParameterType.BOOLEAN, "false",
                        "Steer toward the current target"),
                CoreStageParameter.optional("homing_strength", CoreStageParameterType.DOUBLE, "0.1",
                        "Homing turn strength"),
                CoreStageParameter.optional("particle", CoreStageParameterType.STRING, "flame",
                        "Trail particle key"),
                CoreStageParameter.optional("damage", CoreStageParameterType.DOUBLE, "0",
                        "Damage dealt on hit, zero means none"),
                CoreStageParameter.optional("direction", CoreStageParameterType.STRING, "look",
                        "Initial direction: look or target"));
        this.executionDispatcher = executionDispatcher;
        this.owner = owner;
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        LivingEntity caster = StageSupport.livingEntity(context.caster());
        if (caster == null) {
            return CoreActionOutcome.skipped("action.stage.projectile.no_caster_entity");
        }
        Location start = caster.getEyeLocation();
        World world = start.getWorld();
        if (world == null) {
            return CoreActionOutcome.skipped("action.stage.projectile.no_world");
        }
        if (executionDispatcher == null || owner == null) {
            return CoreActionOutcome.skipped("action.stage.projectile.no_scheduler");
        }

        Settings settings = Settings.of(arguments);
        Location aim = aimLocation(context, settings.direction(), start);
        Vector velocity = initialVelocity(start, aim, settings.speed());
        CoreCancellationToken cancellation = context.get(CoreActionKeys.CANCELLATION).orElse(null);
        new Flight(settings, caster, start.clone(), velocity, aim, cancellation).scheduleNextTick();
        return CoreActionOutcome.success(Map.of("launched", true));
    }

    private Location aimLocation(CoreStageContext context, String direction, Location start) {
        if (!"target".equals(direction)) {
            return null;
        }
        Location target = context.currentTarget().location();
        if (target == null || target.getWorld() != start.getWorld()) {
            return null;
        }
        return target.clone().add(0D, 1D, 0D);
    }

    private Vector initialVelocity(Location start, Location aim, double speed) {
        Vector direction = aim == null
                ? start.getDirection()
                : aim.toVector().subtract(start.toVector());
        if (direction.lengthSquared() <= 0D) {
            direction = start.getDirection();
        }
        return direction.normalize().multiply(speed);
    }

    /**
     * The resolved arguments of one launch.
     *
     * @param speed blocks travelled per tick
     * @param gravity downward pull applied per tick
     * @param lifetime maximum lifetime in ticks
     * @param hitRadius hit detection radius
     * @param pierce how many extra entities the projectile passes through
     * @param homing whether to steer toward the aim location
     * @param homingStrength homing turn strength
     * @param particle trail particle, {@code null} when the key is unknown
     * @param damage damage dealt on hit, zero means none
     * @param direction {@code look} or {@code target}
     */
    private record Settings(double speed,
            double gravity,
            int lifetime,
            double hitRadius,
            int pierce,
            boolean homing,
            double homingStrength,
            Particle particle,
            double damage,
            String direction) {

        static Settings of(CoreResolvedArguments arguments) {
            String particleKey = Texts.trim(arguments.getString("particle", "flame"));
            Particle particle = ValueParsers.parseParticle(particleKey);
            return new Settings(arguments.getDouble("speed", 1.5D),
                    arguments.getDouble("gravity", 0.05D),
                    Math.max(0, arguments.getInt("lifetime", 60)),
                    Math.max(0D, arguments.getDouble("hit_radius", 0.5D)),
                    Math.max(0, arguments.getInt("pierce", 0)),
                    arguments.getBoolean("homing", false),
                    arguments.getDouble("homing_strength", 0.1D),
                    particle,
                    arguments.getDouble("damage", 0D),
                    Texts.trim(arguments.getString("direction", "look")).toLowerCase(Locale.ROOT));
        }
    }

    /**
     * One projectile in flight.
     *
     * <p>Each tick re-schedules itself at its own current position, so a projectile crossing a Folia region
     * boundary continues on the thread that owns the new region rather than touching the old one.</p>
     */
    private final class Flight {

        private final Settings settings;
        private final LivingEntity caster;
        private final Location current;
        private final Vector velocity;
        private final Location aim;
        private final CoreCancellationToken cancellation;
        private final List<Entity> alreadyHit = new ArrayList<>();
        private int ticksLived;
        private int pierceRemaining;

        private Flight(Settings settings,
                LivingEntity caster,
                Location current,
                Vector velocity,
                Location aim,
                CoreCancellationToken cancellation) {
            this.settings = settings;
            this.caster = caster;
            this.current = current;
            this.velocity = velocity;
            this.aim = aim;
            this.cancellation = cancellation;
            this.pierceRemaining = settings.pierce();
        }

        private void scheduleNextTick() {
            if (cancelled() || ticksLived >= settings.lifetime()) {
                return;
            }
            ticksLived++;
            velocity.setY(velocity.getY() - settings.gravity());
            applyHoming();
            current.add(velocity);
            if (current.getWorld() == null) {
                return;
            }
            executionDispatcher.runAtLocationLater(owner, current.clone(), this::tick, 1L);
        }

        private void applyHoming() {
            if (!settings.homing() || aim == null || aim.getWorld() != current.getWorld()) {
                return;
            }
            Vector toTarget = aim.toVector().subtract(current.toVector());
            if (toTarget.lengthSquared() <= 0.0001D) {
                return;
            }
            velocity.add(toTarget.normalize().multiply(settings.homingStrength()));
            if (velocity.lengthSquared() > 0D) {
                velocity.normalize().multiply(settings.speed());
            }
        }

        private void tick() {
            if (cancelled()) {
                return;
            }
            World world = current.getWorld();
            if (world == null || current.getBlock().getType().isSolid()) {
                return;
            }
            if (settings.particle() != null) {
                world.spawnParticle(settings.particle(), current, 1, 0D, 0D, 0D, 0D);
            }
            if (applyHits(world)) {
                return;
            }
            scheduleNextTick();
        }

        /** {@return whether the projectile stopped on this tick} */
        private boolean applyHits(World world) {
            Collection<Entity> nearby = world.getNearbyEntities(current,
                    settings.hitRadius(), settings.hitRadius(), settings.hitRadius());
            for (Entity entity : nearby) {
                if (!(entity instanceof LivingEntity living)
                        || entity.equals(caster)
                        || alreadyHit.contains(entity)) {
                    continue;
                }
                alreadyHit.add(entity);
                if (settings.damage() > 0D) {
                    living.damage(settings.damage(), caster);
                }
                if (pierceRemaining <= 0) {
                    return true;
                }
                pierceRemaining--;
            }
            return false;
        }

        private boolean cancelled() {
            return cancellation != null && cancellation.cancelled();
        }
    }
}
