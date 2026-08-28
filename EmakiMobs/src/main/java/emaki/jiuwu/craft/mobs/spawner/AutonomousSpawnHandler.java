package emaki.jiuwu.craft.mobs.spawner;

import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.corelib.schedule.cron.CronParseException;
import emaki.jiuwu.craft.corelib.schedule.cron.CronScheduler;
import emaki.jiuwu.craft.mobs.service.MobFactory;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public final class AutonomousSpawnHandler implements SpawnHandler {

    private final Plugin plugin;
    private final MobIdentifier mobIdentifier;
    private final MobFactory mobFactory;
    private final CronScheduler cronScheduler = new CronScheduler();
    private final List<ScheduledTask> tasks = new CopyOnWriteArrayList<>();
    private final List<AutonomousSpawnRule> dayIntervalRules = new CopyOnWriteArrayList<>();
    private final Map<String, NamespacedKey> pdcKeyCache = new HashMap<>();

    public AutonomousSpawnHandler(Plugin plugin,
                                  MobIdentifier mobIdentifier,
                                  MobFactory mobFactory) {
        this.plugin = plugin;
        this.mobIdentifier = mobIdentifier;
        this.mobFactory = mobFactory;
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
                                    && countGlobal(r.mobId()) >= r.maxGlobal()) return;
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
                                    && countGlobal(r.mobId()) >= r.maxGlobal()) return;
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
                                && countGlobal(r.mobId()) >= r.maxGlobal()) return;
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
                && countGlobal(rule.mobId()) >= rule.maxGlobal()) {
            return;
        }
        for (Player player : world.getPlayers()) {
            attemptSpawnForPlayer(player, rule);
        }
    }

    private void attemptSpawnForPlayer(Player player, AutonomousSpawnRule rule) {
        Location origin = player.getLocation();
        for (int i = 0; i < 6; i++) {
            Location candidate = findSurface(
                    origin, rule.distance().min(), rule.distance().max());
            if (candidate == null) continue;
            int y = candidate.getBlockY();
            if (y < rule.yMin() || y > rule.yMax()) continue;
            if (rule.lightLevelMax() < 15
                    && candidate.getBlock().getLightLevel() > rule.lightLevelMax()) continue;
            if (!rule.biomes().isEmpty()
                    && !rule.biomes().contains(candidate.getBlock().getBiome())) continue;
            if (!rule.structures().isEmpty()
                    && !isInStructures(candidate, rule.structures())) continue;
            if (!matchesTimeOfDay(candidate.getWorld(), rule.timeOfDay())) continue;
            if (rule.requireSurface()
                    && candidate.getBlock().getLightFromSky() < 15) continue;
            if (rule.maxNearby() > 0
                    && countNearby(candidate, rule.mobId(), 64) >= rule.maxNearby()) continue;
            if (rule.condition().configured()
                    && !ConditionEvaluator.evaluate(rule.condition(),
                    text -> PlaceholderRenderer.renderPapi(player, text, null, "mob_spawn_condition"))) continue;
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

    private long countGlobal(String mobId) {
        return Bukkit.getWorlds().stream()
                .flatMap(w -> w.getLivingEntities().stream())
                .filter(e -> mobId.equals(mobIdentifier.readId(e)))
                .count();
    }

    private long countNearby(Location location, String mobId, int radius) {
        World world = location.getWorld();
        if (world == null) return 0;
        return world.getNearbyEntities(location, radius, radius, radius).stream()
                .filter(e -> e instanceof LivingEntity le && mobId.equals(mobIdentifier.readId(le)))
                .count();
    }

    private Location findSurface(Location center, int minDist, int maxDist) {
        World world = center.getWorld();
        if (world == null) return null;
        double angle = ThreadLocalRandom.current().nextDouble() * 2 * Math.PI;
        int dist = minDist >= maxDist ? minDist : ThreadLocalRandom.current().nextInt(minDist, maxDist + 1);
        int x = (int) (center.getX() + Math.cos(angle) * dist);
        int z = (int) (center.getZ() + Math.sin(angle) * dist);
        int y = world.getHighestBlockYAt(x, z);
        Location candidate = new Location(world, x + 0.5, y + 1.0, z + 0.5);
        Block above = candidate.getBlock();
        Block ground = above.getRelative(0, -1, 0);
        if (!above.isPassable() || !ground.getType().isSolid()) return null;
        return candidate;
    }

    private boolean isInStructures(Location location, List<Structure> structures) {
        Chunk chunk = location.getChunk();
        for (var gen : chunk.getStructures()) {
            if (structures.contains(gen.getStructure())
                    && gen.getBoundingBox().contains(
                            location.getX(), location.getY(), location.getZ())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesTimeOfDay(World world, String timeOfDay) {
        if (timeOfDay == null || "any".equalsIgnoreCase(timeOfDay)) return true;
        long time = world.getTime();
        return switch (timeOfDay.toLowerCase()) {
            case "day"   -> time >= 1000 && time < 13000;
            case "night" -> time >= 13000 || time < 1000;
            default      -> true;
        };
    }
}
