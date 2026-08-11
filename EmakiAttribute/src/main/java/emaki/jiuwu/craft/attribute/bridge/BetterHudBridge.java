package emaki.jiuwu.craft.attribute.bridge;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.api.event.EmakiAttributeDamageEvent;
import emaki.jiuwu.craft.attribute.api.event.PlayerAttributePointAllocateEvent;
import emaki.jiuwu.craft.attribute.api.event.PlayerResourceConsumeEvent;
import emaki.jiuwu.craft.attribute.api.model.DamageContextVariables;
import kr.toxicity.hud.api.BetterHud;
import kr.toxicity.hud.api.manager.TriggerManager;
import kr.toxicity.hud.api.trigger.HudTrigger;
import kr.toxicity.hud.api.update.UpdateEvent;
import kr.toxicity.hud.api.update.UpdateReason;

/**
 * BetterHUD soft-dependency bridge.
 *
 * <p>Registers 11 custom trigger IDs with BetterHUD's {@link TriggerManager} so
 * that administrators can bind any popup/HUD element to EmakiAttribute events
 * directly in their BetterHUD YAML configuration.
 *
 * <p>This class must only be instantiated when BetterHUD is present on the
 * classpath.  The owning plugin checks availability before calling
 * {@link EmakiAttributePlugin#ensureBetterHudBridge()}.
 */
public final class BetterHudBridge implements Listener {

    /** Percent threshold below which emaki_resource_low fires (configurable). */
    // TODO: expose via config.yml key betterhud.resource-low-threshold
    private static final double DEFAULT_RESOURCE_LOW_THRESHOLD = 0.2D;

    private final EmakiAttributePlugin plugin;

    /* One consumer list per trigger; populated by BetterHUD via registerEvent(). */
    private final List<BiConsumer<UUID, UpdateEvent>> meleeHitConsumers      = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, UpdateEvent>> rangedHitConsumers     = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, UpdateEvent>> sweepHitConsumers      = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, UpdateEvent>> criticalHitConsumers   = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, UpdateEvent>> killConsumers          = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, UpdateEvent>> damageTakenConsumers   = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, UpdateEvent>> dodgeSuccessConsumers  = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, UpdateEvent>> shieldBlockConsumers   = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, UpdateEvent>> resourceConsumedConsumers = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, UpdateEvent>> resourceLowConsumers   = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<UUID, UpdateEvent>> pointAllocatedConsumers = new CopyOnWriteArrayList<>();

    public BetterHudBridge(@NotNull EmakiAttributePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers all 11 trigger IDs with BetterHUD's {@link TriggerManager}.
     * Must be called once after BetterHUD is confirmed to be enabled.
     * BetterHUD will call {@code registerEvent()} on each trigger again after
     * a reload — no need to re-register.
     */
    public void registerTriggers() {
        TriggerManager tm = BetterHud.getInstance().getTriggerManager();
        addTrigger(tm, "emaki_melee_hit",         meleeHitConsumers);
        addTrigger(tm, "emaki_ranged_hit",        rangedHitConsumers);
        addTrigger(tm, "emaki_sweep_hit",         sweepHitConsumers);
        addTrigger(tm, "emaki_critical_hit",      criticalHitConsumers);
        addTrigger(tm, "emaki_kill",              killConsumers);
        addTrigger(tm, "emaki_damage_taken",      damageTakenConsumers);
        addTrigger(tm, "emaki_dodge_success",     dodgeSuccessConsumers);
        addTrigger(tm, "emaki_shield_block",      shieldBlockConsumers);
        addTrigger(tm, "emaki_resource_consumed", resourceConsumedConsumers);
        addTrigger(tm, "emaki_resource_low",      resourceLowConsumers);
        addTrigger(tm, "emaki_point_allocated",   pointAllocatedConsumers);
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static void addTrigger(@NotNull TriggerManager tm,
            @NotNull String name,
            @NotNull List<BiConsumer<UUID, UpdateEvent>> consumers) {
        tm.addTrigger(name, yaml -> new HudTrigger<Object>() {
            @Override
            public @NotNull Object getKey(@NotNull Object o) {
                return UUID.randomUUID();
            }

            @Override
            public void registerEvent(@NotNull BiConsumer<UUID, UpdateEvent> consumer) {
                consumers.add(consumer);
            }
        });
    }

    private static void fire(@NotNull List<BiConsumer<UUID, UpdateEvent>> consumers,
            @NotNull UUID playerId) {
        if (consumers.isEmpty()) {
            return;
        }
        // Each fire call creates a unique UpdateEvent key so BetterHUD does
        // not de-duplicate successive identical triggers for the same player.
        UpdateEvent event = new UpdateEvent() {
            private final Object key = UUID.randomUUID();

            @Override
            public @NotNull Object getKey() {
                return key;
            }

            @Override
            public @NotNull UpdateReason getType() {
                return UpdateReason.OTHER;
            }
        };
        for (BiConsumer<UUID, UpdateEvent> consumer : consumers) {
            consumer.accept(playerId, event);
        }
    }

    // ── event handlers ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttributeDamage(@NotNull EmakiAttributeDamageEvent event) {
        double finalDamage = event.getFinalDamage();
        LivingEntity attacker = event.getAttacker();
        LivingEntity target   = event.getTarget();
        DamageContextVariables vars = event.getVariables();

        // ── attacker-side ──────────────────────────────────────────────────
        if (attacker instanceof Player attackerPlayer && finalDamage > 0D) {
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
                fire(sweepHitConsumers, attackerPlayer.getUniqueId());
            } else if (event.getProjectile() != null) {
                fire(rangedHitConsumers, attackerPlayer.getUniqueId());
            } else {
                fire(meleeHitConsumers, attackerPlayer.getUniqueId());
            }
            if (event.isCritical()) {
                fire(criticalHitConsumers, attackerPlayer.getUniqueId());
            }
            // Kill: target health before this hit minus finalDamage falls to 0
            if (target != null && target.getHealth() - finalDamage <= 0D) {
                fire(killConsumers, attackerPlayer.getUniqueId());
            }
        }

        // ── target-side ────────────────────────────────────────────────────
        if (target instanceof Player targetPlayer) {
            if (finalDamage > 0D) {
                fire(damageTakenConsumers, targetPlayer.getUniqueId());
                if (vars.getDouble("target_blocking", 0D) >= 1.0D) {
                    fire(shieldBlockConsumers, targetPlayer.getUniqueId());
                }
            }
            if (vars.getBoolean(false, "dodged")) {
                fire(dodgeSuccessConsumers, targetPlayer.getUniqueId());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResourceConsume(@NotNull PlayerResourceConsumeEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        fire(resourceConsumedConsumers, playerId);

        double max = event.getCurrentMax();
        if (max > 0D) {
            double percentAfter = (event.getCurrentValue() - event.getAmount()) / max;
            double threshold = plugin.getConfig()
                    .getDouble("betterhud.resource-low-threshold", DEFAULT_RESOURCE_LOW_THRESHOLD);
            if (percentAfter <= threshold) {
                fire(resourceLowConsumers, playerId);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPointAllocate(@NotNull PlayerAttributePointAllocateEvent event) {
        fire(pointAllocatedConsumers, event.getPlayer().getUniqueId());
    }
}

