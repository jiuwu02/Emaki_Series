package emaki.jiuwu.craft.mobs.threat;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.selector.PlayerScoreSnapshot;
import emaki.jiuwu.craft.mobs.selector.ScoreSnapshotService;
import emaki.jiuwu.craft.mobs.selector.TargetSelectorService;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public final class ThreatTableManager implements Listener {

    private final Map<UUID, Map<UUID, Double>> tables = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> firstDamagers = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastDamagers = new ConcurrentHashMap<>();
    private final Map<UUID, LockEntry> lockingEntities = new ConcurrentHashMap<>();
    private final AtomicLong decayCycles = new AtomicLong();
    private final AtomicLong generation = new AtomicLong();

    private final Plugin plugin;
    private final ExecutionDispatcher executionDispatcher;
    private final MobIdentifier mobIdentifier;
    private final Supplier<Map<String, MobSpec>> registry;
    private final ScoreSnapshotService snapshots;
    private final TaskToken decayTask;

    private volatile boolean closed;

    private volatile TargetSelectorService targetSelectorService;

    public ThreatTableManager(Plugin plugin,
                               ExecutionDispatcher executionDispatcher,
                               MobIdentifier mobIdentifier,
                               Supplier<Map<String, MobSpec>> registry,
                               ScoreSnapshotService snapshots) {
        this.plugin = plugin;
        this.executionDispatcher = executionDispatcher;
        this.mobIdentifier = mobIdentifier;
        this.registry = registry;
        this.snapshots = snapshots;
        this.decayTask = scheduleDecay();
    }

    public void setTargetSelectorService(TargetSelectorService targetSelectorService) {
        this.targetSelectorService = targetSelectorService;
    }

    public double threat(UUID entityUid, UUID playerUid) {
        Map<UUID, Double> table = tables.get(entityUid);
        return table == null ? 0D : table.getOrDefault(playerUid, 0D);
    }

    @Nullable
    public UUID firstDamager(UUID entityUid) {
        return firstDamagers.get(entityUid);
    }

    @Nullable
    public UUID lastDamager(UUID entityUid) {
        return lastDamagers.get(entityUid);
    }

    public void reload() {
        generation.incrementAndGet();
        lockingEntities.clear();
    }

    public void close() {
        closed = true;
        generation.incrementAndGet();
        decayTask.cancel();
        tables.clear();
        firstDamagers.clear();
        lastDamagers.clear();
        lockingEntities.clear();
        targetSelectorService = null;
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
        if (spec == null) return;
        updateLockTracking(mob, spec);
        if (spec.targetSelector() != null) {
            TargetSelectorService selectorService = targetSelectorService;
            Player selected = selectorService == null
                    ? null : selectorService.select(mob, spec.targetSelector());
            if (selected != null) event.setTarget(selected);
            return;
        }
        if (spec.threatConfig() == null || !spec.threatConfig().enabled()) return;
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
        if (spec == null) return;
        UUID mobUid = mob.getUniqueId();
        UUID playerUid = player.getUniqueId();
        firstDamagers.putIfAbsent(mobUid, playerUid);
        lastDamagers.put(mobUid, playerUid);
        updateLockTracking(mob, spec);
        if (spec.threatConfig() == null || !spec.threatConfig().enabled()) return;
        double weight = spec.threatConfig().weights().damage();
        addThreat(mobUid, playerUid, event.getDamage() * weight);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID playerUid = player.getUniqueId();
        double amount = event.getAmount();
        player.getNearbyEntities(32, 32, 32).forEach(nearby -> {
            if (!(nearby instanceof LivingEntity mob)) return;
            executionDispatcher.runEntity(plugin, mob, () -> {
                String mobId = mobIdentifier.readId(mob);
                if (mobId == null) return;
                MobSpec spec = registry.get().get(mobId);
                if (spec == null || spec.threatConfig() == null || !spec.threatConfig().enabled()) return;
                double weight = spec.threatConfig().weights().healing();
                addThreat(mob.getUniqueId(), playerUid, amount * weight);
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        removeEntityState(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        removeEntityState(event.getEntity().getUniqueId());
    }

    private void removeEntityState(UUID entityUid) {
        tables.remove(entityUid);
        firstDamagers.remove(entityUid);
        lastDamagers.remove(entityUid);
        lockingEntities.remove(entityUid);
    }

    private TaskToken scheduleDecay() {
        return executionDispatcher.runGlobalTimer(plugin, () -> {
            if (closed) return;
            long cycle = decayCycles.incrementAndGet();
            long expectedGeneration = generation.get();
            tables.forEach((entityUid, table) -> {
                LivingEntity entity = mobIdentifier.trackedEntity(entityUid);
                if (entity == null) {
                    removeEntityState(entityUid);
                    return;
                }
                executionDispatcher.runEntity(plugin, entity,
                        () -> processThreatTable(entity, table, expectedGeneration),
                        () -> removeEntityState(entityUid));
            });
            lockingEntities.forEach((entityUid, entry) -> {
                if (cycle % entry.intervalCycles() != 0L) return;
                executionDispatcher.runEntity(plugin, entry.entity(),
                        () -> lockTarget(entityUid, entry, expectedGeneration),
                        () -> lockingEntities.remove(entityUid, entry));
            });
        }, 20L, 20L);
    }

    private void processThreatTable(LivingEntity entity,
            Map<UUID, Double> table,
            long expectedGeneration) {
        if (closed || expectedGeneration != generation.get() || !entity.isValid()) return;
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
            evictOutOfRange(entity, table, maxRange);
        }
        if (table.isEmpty()) {
            tables.remove(entity.getUniqueId(), table);
        }
    }

    private void updateLockTracking(LivingEntity entity, MobSpec spec) {
        UUID entityUid = entity.getUniqueId();
        if (spec.targetSelector() == null || spec.targetLockConfig() == null
                || !spec.targetLockConfig().enabled()) {
            lockingEntities.remove(entityUid);
            return;
        }
        long intervalCycles = Math.max(1L, (spec.targetLockConfig().intervalTicks() + 19L) / 20L);
        lockingEntities.put(entityUid,
                new LockEntry(entity, spec.targetSelector(), intervalCycles));
    }

    private void lockTarget(UUID entityUid, LockEntry entry, long expectedGeneration) {
        if (closed || expectedGeneration != generation.get()
                || lockingEntities.get(entityUid) != entry
                || !(entry.entity() instanceof Mob mob) || !entry.entity().isValid()) {
            return;
        }
        TargetSelectorService selectorService = targetSelectorService;
        Player selected = selectorService == null
                ? null : selectorService.select(entry.entity(), entry.selectorId());
        if (selected != null) {
            mob.setTarget(selected);
        }
    }

    private void evictOutOfRange(LivingEntity entity, Map<UUID, Double> table, double maxRange) {
        var location = entity.getLocation();
        double maxDistanceSquared = maxRange * maxRange;
        table.entrySet().removeIf(entry -> {
            UUID playerUid = entry.getKey();
            if (!snapshots.isTracked(playerUid)) return true;
            PlayerScoreSnapshot snapshot = snapshots.snapshot(playerUid);
            if (snapshot == null) return false;
            if (!location.getWorld().getUID().equals(snapshot.worldId())) return true;
            double dx = location.getX() - snapshot.x();
            double dy = location.getY() - snapshot.y();
            double dz = location.getZ() - snapshot.z();
            return dx * dx + dy * dy + dz * dz > maxDistanceSquared;
        });
    }

    private record LockEntry(LivingEntity entity, String selectorId, long intervalCycles) {
    }
}
