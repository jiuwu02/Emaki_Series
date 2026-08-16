package emaki.jiuwu.craft.mobs.spawner;

import emaki.jiuwu.craft.corelib.schedule.cron.CronParseException;
import emaki.jiuwu.craft.corelib.schedule.cron.CronScheduler;
import emaki.jiuwu.craft.mobs.service.MobFactory;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * autonomous 类型刷新规则的调度执行器。
 * 不监听任何 CreatureSpawnEvent，完全由内部调度循环驱动。
 */
public final class AutonomousSpawnHandler implements SpawnHandler {

    private final Plugin plugin;
    private final SpawnConditionEvaluator conditionEvaluator;
    private final MobFactory mobFactory;
    private final CronScheduler cronScheduler = new CronScheduler();
    private final List<ScheduledTask> tasks = new CopyOnWriteArrayList<>();
    private final List<AutonomousSpawnRule> dayIntervalRules = new CopyOnWriteArrayList<>();
    private final Map<String, NamespacedKey> pdcKeyCache = new HashMap<>();

    public AutonomousSpawnHandler(Plugin plugin,
                                  SpawnConditionEvaluator conditionEvaluator,
                                  MobFactory mobFactory) {
        this.plugin = plugin;
        this.conditionEvaluator = conditionEvaluator;
        this.mobFactory = mobFactory;
        // 永久日计时器：reload 时不取消，仅在插件关闭时由 Bukkit 自动取消
        plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> checkDayIntervalRules(), 1L, 20L);
    }

    @Override
    public void register(SpawnRule rule) {
        if (!(rule instanceof AutonomousSpawnRule r)) return;
        switch (r.trigger()) {
            case INTERVAL -> {
                ScheduledTask task = plugin.getServer().getGlobalRegionScheduler()
                        .runAtFixedRate(plugin, t -> {
                            if (r.maxGlobal() > 0
                                    && conditionEvaluator.countGlobal(r.mobId()) >= r.maxGlobal()) return;
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (!r.worlds().isEmpty()
                                        && !r.worlds().contains(p.getWorld().getName())) continue;
                                attemptSpawnForPlayer(p, r);
                            }
                        }, 1L, r.intervalTicks());
                tasks.add(task);
            }
            case PLAYER_FOLLOW -> {
                ScheduledTask task = plugin.getServer().getGlobalRegionScheduler()
                        .runAtFixedRate(plugin, t -> {
                            if (r.maxGlobal() > 0
                                    && conditionEvaluator.countGlobal(r.mobId()) >= r.maxGlobal()) return;
                            List<Player> candidates = new ArrayList<>(Bukkit.getOnlinePlayers());
                            if (!r.worlds().isEmpty()) {
                                candidates.removeIf(p -> !r.worlds().contains(p.getWorld().getName()));
                            }
                            if (candidates.isEmpty()) return;
                            Player chosen = candidates.get(
                                    ThreadLocalRandom.current().nextInt(candidates.size()));
                            attemptSpawnForPlayer(chosen, r);
                        }, 1L, r.intervalTicks());
                tasks.add(task);
            }
            case DAY_INTERVAL -> dayIntervalRules.add(r);
            case CRON -> {
                String expr = r.cronExpression();
                if (expr == null || expr.isBlank()) {
                    plugin.getLogger().warning(
                            "[EmakiMobs] Mob '" + r.mobId() + "' trigger=cron has no cron expression.");
                    return;
                }
                try {
                    cronScheduler.schedule(plugin, expr, () -> {
                        if (r.maxGlobal() > 0
                                && conditionEvaluator.countGlobal(r.mobId()) >= r.maxGlobal()) return;
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (!r.worlds().isEmpty()
                                    && !r.worlds().contains(p.getWorld().getName())) continue;
                            attemptSpawnForPlayer(p, r);
                        }
                    });
                } catch (CronParseException e) {
                    plugin.getLogger().warning(
                            "[EmakiMobs] Invalid cron '" + expr + "' for mob '" + r.mobId() + "': " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void clear() {
        tasks.forEach(t -> {
            if (!t.isCancelled()) t.cancel();
        });
        tasks.clear();
        dayIntervalRules.clear();
        cronScheduler.cancelAll();
    }

    private void checkDayIntervalRules() {
        if (dayIntervalRules.isEmpty()) return;
        for (World world : Bukkit.getWorlds()) {
            long fullTime = world.getFullTime();
            long currentDay = fullTime / 24000L;
            long worldTime = world.getTime();
            for (AutonomousSpawnRule rule : dayIntervalRules) {
                if (!rule.worlds().isEmpty()
                        && !rule.worlds().contains(world.getName())) {
                    continue;
                }
                NamespacedKey key = pdcKey(rule.mobId());
                long lastDay = readLastSpawnDay(world, key);
                if (currentDay - lastDay < rule.intervalDays()) continue;
                if (rule.onDayStart() && worldTime >= 1000) continue;
                writeLastSpawnDay(world, key, currentDay);
                triggerSpawnForWorld(world, rule);
            }
        }
    }

    private void triggerSpawnForWorld(World world, AutonomousSpawnRule rule) {
        if (rule.maxGlobal() > 0
                && conditionEvaluator.countGlobal(rule.mobId()) >= rule.maxGlobal()) {
            return;
        }
        for (Player player : world.getPlayers()) {
            attemptSpawnForPlayer(player, rule);
        }
    }

    private void attemptSpawnForPlayer(Player player, AutonomousSpawnRule rule) {
        Location origin = player.getLocation();
        for (int i = 0; i < 6; i++) {
            Location candidate = conditionEvaluator.findSurface(
                    origin, rule.distance().min(), rule.distance().max());
            if (candidate == null) continue;
            int y = candidate.getBlockY();
            if (y < rule.yMin() || y > rule.yMax()) continue;
            if (rule.lightLevelMax() < 15
                    && candidate.getBlock().getLightLevel() > rule.lightLevelMax()) continue;
            if (!rule.biomes().isEmpty()
                    && !rule.biomes().contains(candidate.getBlock().getBiome())) continue;
            if (!rule.structures().isEmpty()
                    && !conditionEvaluator.isInStructures(candidate, rule.structures())) continue;
            if (!conditionEvaluator.matchesTimeOfDay(candidate.getWorld(), rule.timeOfDay())) continue;
            if (rule.requireSurface()
                    && candidate.getBlock().getLightFromSky() < 15) continue;
            if (rule.maxNearby() > 0
                    && conditionEvaluator.countNearby(candidate, rule.mobId(), 64) >= rule.maxNearby()) continue;
            int count = rule.count().min() >= rule.count().max()
                    ? rule.count().min()
                    : ThreadLocalRandom.current().nextInt(rule.count().min(), rule.count().max() + 1);
            for (int j = 0; j < count; j++) {
                mobFactory.spawn(candidate, rule.mobId());
            }
            return;
        }
    }

    private NamespacedKey pdcKey(String mobId) {
        return pdcKeyCache.computeIfAbsent(mobId, id -> {
            String sanitized = id.toLowerCase().replaceAll("[^a-z0-9._-]", "_");
            return new NamespacedKey(plugin, "dls_" + sanitized);
        });
    }

    private long readLastSpawnDay(World world, NamespacedKey key) {
        Long value = world.getPersistentDataContainer().get(key, PersistentDataType.LONG);
        return value != null ? value : -1L;
    }

    private void writeLastSpawnDay(World world, NamespacedKey key, long day) {
        world.getPersistentDataContainer().set(key, PersistentDataType.LONG, day);
    }
}
