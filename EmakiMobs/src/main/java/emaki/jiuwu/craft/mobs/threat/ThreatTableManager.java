package emaki.jiuwu.craft.mobs.threat;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ThreatTableManager implements Listener {

    private final Map<UUID, Map<UUID, Double>> tables = new ConcurrentHashMap<>();

    private final MobIdentifier mobIdentifier;
    private final Supplier<Map<String, MobSpec>> registry;
    private final TaskToken decayTask;

    public ThreatTableManager(Plugin plugin,
                               ExecutionDispatcher executionDispatcher,
                               MobIdentifier mobIdentifier,
                               Supplier<Map<String, MobSpec>> registry) {
        this.mobIdentifier = mobIdentifier;
        this.registry = registry;
        this.decayTask = scheduleDecay(plugin, executionDispatcher);
    }

    public void close() {
        decayTask.cancel();
        tables.clear();
    }

    public void addThreat(UUID entityUid, UUID playerUid, double amount) {
        tables.computeIfAbsent(entityUid, k -> new ConcurrentHashMap<>())
                .merge(playerUid, amount, Double::sum);
    }

    @Nullable
    public Player getHighestThreatPlayer(LivingEntity entity) {
        Map<UUID, Double> table = tables.get(entity.getUniqueId());
        if (table == null || table.isEmpty()) return null;
        UUID topUid = null;
        double topValue = 0;
        for (var e : table.entrySet()) {
            if (e.getValue() > topValue) {
                topValue = e.getValue();
                topUid = e.getKey();
            }
        }
        return topUid != null ? Bukkit.getPlayer(topUid) : null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        String mobId = mobIdentifier.readId(mob);
        if (mobId == null) return;
        MobSpec spec = registry.get().get(mobId);
        if (spec == null || spec.threatConfig() == null || !spec.threatConfig().enabled()) return;
        Player top = getHighestThreatPlayer(mob);
        if (top != null) event.setTarget(top);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        String mobId = mobIdentifier.readId(mob);
        if (mobId == null) return;
        MobSpec spec = registry.get().get(mobId);
        if (spec == null || spec.threatConfig() == null || !spec.threatConfig().enabled()) return;
        double weight = spec.threatConfig().weights().damage();
        addThreat(mob.getUniqueId(), player.getUniqueId(), event.getDamage() * weight);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        player.getNearbyEntities(32, 32, 32).forEach(nearby -> {
            if (!(nearby instanceof LivingEntity mob)) return;
            String mobId = mobIdentifier.readId(mob);
            if (mobId == null) return;
            MobSpec spec = registry.get().get(mobId);
            if (spec == null || spec.threatConfig() == null || !spec.threatConfig().enabled()) return;
            double weight = spec.threatConfig().weights().healing();
            addThreat(mob.getUniqueId(), player.getUniqueId(), event.getAmount() * weight);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        tables.remove(event.getEntity().getUniqueId());
    }

    private TaskToken scheduleDecay(Plugin plugin, ExecutionDispatcher executionDispatcher) {
        return executionDispatcher.runGlobalTimer(plugin, () -> {
            tables.forEach((entityUid, table) -> {
                var rawEntity = Bukkit.getEntity(entityUid);
                LivingEntity entity = rawEntity instanceof LivingEntity le ? le : null;
                if (entity == null) { tables.remove(entityUid); return; }
                String mobId = mobIdentifier.readId(entity);
                MobSpec spec = mobId != null ? registry.get().get(mobId) : null;
                double decayRate = spec != null && spec.threatConfig() != null
                        ? spec.threatConfig().decay().rate() : 0.05;
                double maxRange = spec != null && spec.threatConfig() != null
                        ? spec.threatConfig().maxRange() : 64;
                boolean removeOOR = spec != null && spec.threatConfig() != null
                        && spec.threatConfig().decay().outOfRange();
                table.replaceAll((uid, val) -> val * (1.0 - decayRate));
                table.entrySet().removeIf(e -> e.getValue() < 0.001);
                if (removeOOR) {
                    executionDispatcher.runEntity(plugin, entity,
                            () -> evictOutOfRange(entity, table, maxRange));
                }
            });
            tables.entrySet().removeIf(e -> e.getValue().isEmpty());
        }, 20L, 20L);
    }

    private void evictOutOfRange(LivingEntity entity, Map<UUID, Double> table, double maxRange) {
        if (!entity.isValid()) {
            return;
        }
        table.entrySet().removeIf(e -> {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.getWorld().equals(entity.getWorld())) return true;
            return p.getLocation().distance(entity.getLocation()) > maxRange;
        });
    }
}
