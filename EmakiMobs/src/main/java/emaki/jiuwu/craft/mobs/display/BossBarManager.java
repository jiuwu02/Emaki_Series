package emaki.jiuwu.craft.mobs.display;

import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class BossBarManager implements Listener {

    private final Map<UUID, BossBar> entityBars = new ConcurrentHashMap<>();
    private final Plugin plugin;
    private final MobIdentifier mobIdentifier;
    private final Supplier<Map<String, MobSpec>> registry;

    public BossBarManager(Plugin plugin, MobIdentifier mobIdentifier,
                           Supplier<Map<String, MobSpec>> registry) {
        this.plugin = plugin;
        this.mobIdentifier = mobIdentifier;
        this.registry = registry;
        scheduleDistanceCheck();
    }

    @SuppressWarnings("deprecation")
    public void registerIfConfigured(LivingEntity entity, String mobId) {
        MobSpec spec = registry.get().get(mobId);
        if (spec == null || spec.bossBarConfig() == null) return;
        var cfg = spec.bossBarConfig();
        BossBar bar = Bukkit.createBossBar(cfg.title(), cfg.color(), cfg.style());
        double maxHp = entity.getMaxHealth();
        bar.setProgress(maxHp > 0 ? Math.min(1.0, entity.getHealth() / maxHp) : 1.0);
        bar.setVisible(true);
        entityBars.put(entity.getUniqueId(), bar);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        BossBar bar = entityBars.get(entity.getUniqueId());
        if (bar == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> updateBarProgress(entity, bar));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        BossBar bar = entityBars.get(entity.getUniqueId());
        if (bar == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> updateBarProgress(entity, bar));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        BossBar bar = entityBars.remove(event.getEntity().getUniqueId());
        if (bar != null) { bar.removeAll(); bar.setVisible(false); }
    }

    private void updateBarProgress(LivingEntity entity, BossBar bar) {
        if (!entity.isValid()) return;
        double maxHp = entity.getMaxHealth();
        if (maxHp > 0) bar.setProgress(Math.min(1.0, Math.max(0.0, entity.getHealth() / maxHp)));
    }

    private void scheduleDistanceCheck() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            entityBars.forEach((uid, bar) -> {
                var raw = Bukkit.getEntity(uid);
                if (!(raw instanceof LivingEntity entity) || !entity.isValid()) {
                    bar.removeAll(); entityBars.remove(uid); return;
                }
                String mobId = mobIdentifier.readId(entity);
                if (mobId == null) return;
                MobSpec spec = registry.get().get(mobId);
                if (spec == null || spec.bossBarConfig() == null) return;
                double range = spec.bossBarConfig().range();
                Bukkit.getOnlinePlayers().forEach(player -> {
                    if (!player.getWorld().equals(entity.getWorld())) {
                        bar.removePlayer(player); return;
                    }
                    if (player.getLocation().distance(entity.getLocation()) <= range) {
                        bar.addPlayer(player);
                    } else {
                        bar.removePlayer(player);
                    }
                });
            });
        }, 20L, 20L);
    }
}
