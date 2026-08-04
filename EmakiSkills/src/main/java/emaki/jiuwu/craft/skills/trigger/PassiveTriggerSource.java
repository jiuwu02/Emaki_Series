package emaki.jiuwu.craft.skills.trigger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.skills.config.AppConfig;

public final class PassiveTriggerSource {

    private static final long DEFAULT_COMBO_TIMEOUT_TICKS = 60L;

    private final Supplier<AppConfig> configSupplier;
    private final Map<UUID, ComboState> comboStates = new ConcurrentHashMap<>();
    private PassiveTriggerDispatcher dispatcher_ref;
    private final AtomicLong timerGeneration = new AtomicLong();
    private TaskHandle timerTask;
    private long lastTimerDispatchAt;
    private volatile boolean timerDispatchWarningLogged;

    public PassiveTriggerSource(Supplier<AppConfig> configSupplier) {
        this.configSupplier = configSupplier;
    }

    public void register(JavaPlugin plugin, PassiveTriggerDispatcher dispatcher, ExecutionDispatcher executionDispatcher) {
        this.dispatcher_ref = dispatcher;
        plugin.getServer().getPluginManager().registerEvents(new Listener() {

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
                Player attacker = playerFromDamager(event.getDamager());
                if (attacker != null) {
                    trigger(attacker, "attack", event, event.getEntity(), event.getEntity().getLocation(), event.getDamager());
                    dispatchComboAttack(attacker, event, event.getEntity());
                }

                if (event.getEntity() instanceof Player damagedPlayer) {
                    trigger(damagedPlayer, "damaged_by_entity", event,
                            event.getDamager(), event.getDamager().getLocation(), event.getDamager());
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onEntityDamage(EntityDamageEvent event) {
                if (event.getEntity() instanceof Player player) {
                    trigger(player, "damaged", event, null, player.getLocation(), null);
                }
            }

            @EventHandler(priority = EventPriority.MONITOR)
            public void onPlayerDeath(PlayerDeathEvent event) {
                Player player = event.getEntity();
                trigger(player, "death", event, null, player.getLocation(), null);
            }

            @EventHandler(priority = EventPriority.MONITOR)
            public void onEntityDeath(EntityDeathEvent event) {
                Player killer = event.getEntity().getKiller();
                if (killer == null) {
                    return;
                }
                if (event.getEntity() instanceof Player killedPlayer) {
                    trigger(killer, "kill_player", event, killedPlayer, killedPlayer.getLocation(), killedPlayer);
                    return;
                }
                trigger(killer, "kill_entity", event, event.getEntity(), event.getEntity().getLocation(), event.getEntity());
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onShootBow(EntityShootBowEvent event) {
                if (event.getEntity() instanceof Player player) {
                    Entity projectile = event.getProjectile();
                    trigger(player, "shoot_bow", event, null, projectile.getLocation(), projectile);
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onProjectileLaunch(ProjectileLaunchEvent event) {
                if (event.getEntity() instanceof Trident trident && playerFromProjectile(trident) instanceof Player player) {
                    trigger(player, "shoot_trident", event, null, trident.getLocation(), trident);
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onProjectileHit(ProjectileHitEvent event) {
                Projectile projectile = event.getEntity();
                Player shooter = playerFromProjectile(projectile);
                if (shooter == null) {
                    return;
                }

                Entity hitEntity = event.getHitEntity();
                Location hitLocation = hitEntity != null
                        ? hitEntity.getLocation()
                        : event.getHitBlock() != null ? event.getHitBlock().getLocation().add(0.5D, 0.5D, 0.5D) : projectile.getLocation();

                if (projectile instanceof Trident) {
                    trigger(shooter, hitEntity == null ? "trident_land" : "trident_hit",
                            event, hitEntity, hitLocation, projectile);
                    return;
                }
                if (projectile instanceof AbstractArrow) {
                    trigger(shooter, hitEntity == null ? "arrow_land" : "arrow_hit",
                            event, hitEntity, hitLocation, projectile);
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onBlockBreak(BlockBreakEvent event) {
                trigger(event.getPlayer(), "break_block", event, null,
                        event.getBlock().getLocation().add(0.5D, 0.5D, 0.5D), null);
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onBlockPlace(BlockPlaceEvent event) {
                trigger(event.getPlayer(), "place_block", event, null,
                        event.getBlockPlaced().getLocation().add(0.5D, 0.5D, 0.5D), null);
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onDropItem(PlayerDropItemEvent event) {
                trigger(event.getPlayer(), event.getPlayer().isSneaking() ? "shift_drop_item" : "drop_item",
                        event, event.getItemDrop(), event.getItemDrop().getLocation(), event.getItemDrop());
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onSwapHandItems(PlayerSwapHandItemsEvent event) {
                trigger(event.getPlayer(), event.getPlayer().isSneaking() ? "shift_swap_items" : "swap_items",
                        event, null, event.getPlayer().getLocation(), null);
            }

            @EventHandler(priority = EventPriority.MONITOR)
            public void onJoin(PlayerJoinEvent event) {
                trigger(event.getPlayer(), "login", event, null, event.getPlayer().getLocation(), null);
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onToggleSneak(PlayerToggleSneakEvent event) {
                if (event.isSneaking()) {
                    trigger(event.getPlayer(), "sneak", event, null, event.getPlayer().getLocation(), null);
                }
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onTeleport(PlayerTeleportEvent event) {
                trigger(event.getPlayer(), "teleport", event, null, event.getTo(), null);
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            public void onConsume(PlayerItemConsumeEvent event) {
                trigger(event.getPlayer(), "consume", event, null, event.getPlayer().getLocation(), null);
            }

            private void trigger(Player player,
                    String triggerId,
                    Event event,
                    Entity targetEntity,
                    Location targetLocation,
                    Entity sourceEntity) {
                dispatcher.dispatch(new TriggerInvocation(
                        player,
                        triggerId,
                        event,
                        player.isSneaking(),
                        false,
                        System.currentTimeMillis(),
                        targetEntity,
                        targetLocation,
                        sourceEntity
                ));
            }
        }, plugin);

        stop();
        long generation = timerGeneration.incrementAndGet();
        timerTask = executionDispatcher.runGlobalTimer(plugin, () -> {
            if (timerGeneration.get() != generation) {
                return;
            }
            long intervalMillis = timerIntervalTicks() * 50L;
            long now = System.currentTimeMillis();
            if (now - lastTimerDispatchAt < intervalMillis) {
                return;
            }
            lastTimerDispatchAt = now;
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                try {
                    executionDispatcher.runEntity(plugin, player, () -> {
                        if (timerGeneration.get() != generation || !player.isOnline()) {
                            return;
                        }
                        dispatcher.dispatch(new TriggerInvocation(
                                player,
                                "timer",
                                null,
                                player.isSneaking(),
                                false,
                                now,
                                null,
                                player.getLocation(),
                                null
                        ));
                    }, () -> { });
                    timerDispatchWarningLogged = false;
                } catch (Throwable throwable) {
                    if (!timerDispatchWarningLogged) {
                        timerDispatchWarningLogged = true;
                        plugin.getLogger().log(Level.WARNING,
                                "Passive trigger dispatch failed: trigger=timer, player=" + player.getName()
                                        + ", operation=timer_dispatch, cause=" + throwable
                                        + " (further identical warnings suppressed until the next successful dispatch)",
                                throwable);
                    }
                }
            }
        }, 1L, 1L);
    }

    public void stop() {
        timerGeneration.incrementAndGet();
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private long timerIntervalTicks() {
        AppConfig config = configSupplier.get();
        return config == null ? 20L : config.passiveTriggerSettings().timerIntervalTicks();
    }

    private Player playerFromDamager(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            return playerFromProjectile(projectile);
        }
        return null;
    }

    private Player playerFromProjectile(Projectile projectile) {
        ProjectileSource shooter = projectile.getShooter();
        return shooter instanceof Player player ? player : null;
    }

    private void dispatchComboAttack(Player attacker, Event event, Entity target) {
        UUID playerId = attacker.getUniqueId();
        long currentTick = attacker.getWorld().getFullTime();
        long comboTimeout = comboTimeoutTicks();

        ComboState state = comboStates.get(playerId);
        int newCount;
        if (state == null || (currentTick - state.lastHitTick()) > comboTimeout) {
            newCount = 1;
        } else {
            newCount = state.count() + 1;
        }
        comboStates.put(playerId, new ComboState(newCount, currentTick));

        Map<String, Object> extras = Map.of("combo_count", newCount);
        dispatcher_ref.dispatch(new TriggerInvocation(
                attacker,
                "combo_attack",
                event,
                attacker.isSneaking(),
                false,
                System.currentTimeMillis(),
                target,
                target == null ? attacker.getLocation() : target.getLocation(),
                attacker,
                extras
        ));
    }

    private long comboTimeoutTicks() {
        AppConfig config = configSupplier.get();
        if (config == null) {
            return DEFAULT_COMBO_TIMEOUT_TICKS;
        }
        return config.passiveTriggerSettings().comboTimeoutTicks();
    }

    private record ComboState(int count, long lastHitTick) {
    }
}
