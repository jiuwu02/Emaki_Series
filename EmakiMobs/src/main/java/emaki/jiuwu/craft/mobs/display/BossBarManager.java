package emaki.jiuwu.craft.mobs.display;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.mobs.EmakiMobsPlugin;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class BossBarManager implements Listener {

    private final Map<UUID, BossBar> entityBars = new ConcurrentHashMap<>();
    private final EmakiMobsPlugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final MobIdentifier mobIdentifier;
    private final Supplier<Map<String, MobSpec>> registry;
    private final TaskToken distanceTask;

    public BossBarManager(EmakiMobsPlugin plugin, ExecutionDispatcher executionDispatcher,
                           MobIdentifier mobIdentifier,
                           Supplier<Map<String, MobSpec>> registry) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
        this.mobIdentifier = mobIdentifier;
        this.registry = registry;
        this.distanceTask = scheduleDistanceCheck();
    }

    public void close() {
        distanceTask.cancel();
        entityBars.values().forEach(this::clearViewers);
        entityBars.clear();
    }

    public void registerIfConfigured(LivingEntity entity, String mobId) {
        MobSpec spec = registry.get().get(mobId);
        if (spec == null || spec.bossBarConfig() == null) return;
        var cfg = spec.bossBarConfig();
        BossBar bar = BossBar.bossBar(MiniMessages.parse(cfg.title()), progress(entity),
                toColor(cfg.color()), toOverlay(cfg.style()));
        BossBar previous = entityBars.put(entity.getUniqueId(), bar);
        if (previous != null) {
            debug("re-register", entity, "mobId=" + mobId + " previousCleared=true");
            clearViewers(previous);
        } else {
            debug("register", entity, "mobId=" + mobId
                    + " range=" + cfg.range()
                    + " title=" + MiniMessages.plainText(MiniMessages.parse(cfg.title())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        BossBar bar = entityBars.get(entity.getUniqueId());
        if (bar == null) return;
        executionDispatcher.runEntity(plugin, entity, () -> updateBarProgress(entity, bar));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        BossBar bar = entityBars.get(entity.getUniqueId());
        if (bar == null) return;
        executionDispatcher.runEntity(plugin, entity, () -> updateBarProgress(entity, bar));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        BossBar bar = entityBars.remove(event.getEntity().getUniqueId());
        if (bar != null) {
            debug("death_clear", event.getEntity(), "viewersCleared=true");
            clearViewers(bar);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        BossBar bar = entityBars.remove(entity.getUniqueId());
        if (bar != null) {
            debug("remove_clear", entity,
                    "reason=entity_left_world (kill/remove/chunk_unload/death)");
            clearViewers(bar);
        }
    }

    @SuppressWarnings("deprecation")
    private static float progress(LivingEntity entity) {
        double maxHp = entity.getMaxHealth();
        if (!(maxHp > 0)) return 1.0F;
        double ratio = Math.min(1.0, Math.max(0.0, entity.getHealth() / maxHp));
        if (Double.isNaN(ratio)) return 1.0F;
        return (float) ratio;
    }

    private void updateBarProgress(LivingEntity entity, BossBar bar) {
        if (!entity.isValid()) return;
        bar.progress(progress(entity));
    }

    private void clearViewers(BossBar bar) {
        Bukkit.getOnlinePlayers().forEach(bar::removeViewer);
    }

    private void debug(String step, LivingEntity entity, String detail) {
        var logger = plugin.debugLogger();
        if (logger == null || !logger.shouldLog("mobs", entity.getUniqueId())) {
            return;
        }
        plugin.getLogger().info("[DEBUG:mobs:bossbar:" + step + "] entity=" + entity.getUniqueId()
                + " type=" + entity.getType().getKey() + " " + detail);
    }

    private void debug(String step, UUID entityId, String detail) {
        var logger = plugin.debugLogger();
        if (logger == null || !logger.shouldLog("mobs", entityId)) {
            return;
        }
        plugin.getLogger().info("[DEBUG:mobs:bossbar:" + step + "] entity=" + entityId + " " + detail);
    }

    private static BossBar.Color toColor(BarColor color) {
        return switch (color) {
            case PINK -> BossBar.Color.PINK;
            case BLUE -> BossBar.Color.BLUE;
            case RED -> BossBar.Color.RED;
            case GREEN -> BossBar.Color.GREEN;
            case YELLOW -> BossBar.Color.YELLOW;
            case PURPLE -> BossBar.Color.PURPLE;
            case WHITE -> BossBar.Color.WHITE;
        };
    }

    private static BossBar.Overlay toOverlay(BarStyle style) {
        return switch (style) {
            case SOLID -> BossBar.Overlay.PROGRESS;
            case SEGMENTED_6 -> BossBar.Overlay.NOTCHED_6;
            case SEGMENTED_10 -> BossBar.Overlay.NOTCHED_10;
            case SEGMENTED_12 -> BossBar.Overlay.NOTCHED_12;
            case SEGMENTED_20 -> BossBar.Overlay.NOTCHED_20;
        };
    }

    private TaskToken scheduleDistanceCheck() {
        return executionDispatcher.runGlobalTimer(plugin, () -> {
            entityBars.forEach((uid, bar) -> {
                var raw = Bukkit.getEntity(uid);
                if (!(raw instanceof LivingEntity entity) || !entity.isValid()) {
                    debug("stale_clear", uid,
                            "entityAbsentOrInvalid=true (expected: still tracked with a live entity)");
                    clearViewers(bar);
                    entityBars.remove(uid);
                    return;
                }
                executionDispatcher.runEntity(plugin, entity, () -> refreshViewers(entity, bar));
            });
        }, 20L, 20L);
    }

    private void refreshViewers(LivingEntity entity, BossBar bar) {
        if (!entity.isValid()) {
            return;
        }
        String mobId = mobIdentifier.readId(entity);
        if (mobId == null) return;
        MobSpec spec = registry.get().get(mobId);
        if (spec == null || spec.bossBarConfig() == null) return;
        double range = spec.bossBarConfig().range();
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (!player.getWorld().equals(entity.getWorld())) {
                bar.removeViewer(player); return;
            }
            if (player.getLocation().distance(entity.getLocation()) <= range) {
                bar.addViewer(player);
            } else {
                bar.removeViewer(player);
            }
        });
    }
}
