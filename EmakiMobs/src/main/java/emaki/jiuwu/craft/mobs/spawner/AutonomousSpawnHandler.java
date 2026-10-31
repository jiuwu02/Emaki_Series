package emaki.jiuwu.craft.mobs.spawner;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.corelib.schedule.cron.CronParseException;
import emaki.jiuwu.craft.corelib.schedule.cron.CronScheduler;
import emaki.jiuwu.craft.mobs.service.MobFactory;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class AutonomousSpawnHandler implements SpawnHandler {

    private final Plugin plugin;
    private final MobIdentifier mobIdentifier;
    private final MobFactory mobFactory;
    private final ExecutionDispatcher dispatcher;
    private final CronScheduler cronScheduler = new CronScheduler();
    private final List<TaskToken> tokens = new CopyOnWriteArrayList<>();
    private final List<AutonomousSpawnRule> dayIntervalRules = new CopyOnWriteArrayList<>();
    private final Map<String, NamespacedKey> pdcKeyCache = new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();
    private volatile TaskToken heartbeatTask;

    public AutonomousSpawnHandler(Plugin plugin,
                                  MobIdentifier mobIdentifier,
                                  MobFactory mobFactory,
                                  ExecutionDispatcher dispatcher) {
        this.plugin = plugin;
        this.mobIdentifier = mobIdentifier;
        this.mobFactory = mobFactory;
        this.dispatcher = dispatcher;
    }

    @Override
    public void register(SpawnRule rule) {
        if (!(rule instanceof AutonomousSpawnRule r)) return;
        ensureHeartbeat();
        long expected = generation.get();
        switch (r.trigger()) {
            case INTERVAL -> track(schedule(() -> dispatcher.runGlobalTimer(plugin,
                    () -> withGlobalLimit(r, expected, () -> dispatchPlayers(r, expected, null)),
                    1L, r.intervalTicks()), "生物 '" + r.mobId() + "' 的间隔刷新"));
            case PLAYER_FOLLOW -> track(schedule(() -> dispatcher.runGlobalTimer(plugin,
                    () -> withGlobalLimit(r, expected, () -> dispatchFollower(r, expected)),
                    1L, r.intervalTicks()), "生物 '" + r.mobId() + "' 的跟随刷新"));
            case DAY_INTERVAL -> dayIntervalRules.add(r);
            case CRON -> {
                String expr = r.cronExpression();
                if (expr == null || expr.isBlank()) {
                    plugin.getLogger().warning(
                            "[spawn] 生物 '" + r.mobId() + "' 的 cron 触发器缺少 cron 表达式");
                    return;
                }
                try {
                    cronScheduler.schedule(plugin, expr,
                            () -> withGlobalLimit(r, expected, () -> dispatchPlayers(r, expected, null)));
                } catch (CronParseException e) {
                    plugin.getLogger().warning(
                            "[spawn] 生物 '" + r.mobId() + "' 的 cron 表达式 '" + expr + "' 无效: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void clear() {
        generation.incrementAndGet();
        for (TaskToken token : tokens) {
            if (!token.cancelled()) token.cancel();
        }
        tokens.clear();
        heartbeatTask = null;
        dayIntervalRules.clear();
        cronScheduler.cancelAll();
    }

    private TaskToken schedule(Supplier<TaskToken> action, String description) {
        try {
            return action.get();
        } catch (RuntimeException exception) {
            if (plugin.isEnabled()) {
                plugin.getLogger().log(Level.WARNING,
                        "[spawn] 无法调度" + description, exception);
            }
            return null;
        }
    }

    private void track(TaskToken token) {
        if (token != null) tokens.add(token);
    }

    private void ensureHeartbeat() {
        TaskToken current = heartbeatTask;
        if (current != null && !current.cancelled()) {
            return;
        }
        heartbeatTask = schedule(() -> dispatcher.runGlobalTimer(
                plugin, this::checkDayIntervalRules, 1L, 20L), "每日刷新心跳");
        track(heartbeatTask);
    }

    private void checkDayIntervalRules() {
        if (dayIntervalRules.isEmpty()) return;
        long expected = generation.get();
        for (World world : Bukkit.getWorlds()) {
            schedule(() -> dispatcher.runAtLocation(plugin, worldAnchor(world),
                    () -> checkDayIntervalForWorld(world, expected)),
                    "世界 '" + world.getName() + "' 的每日刷新检查");
        }
    }

    private void checkDayIntervalForWorld(World world, long expected) {
        if (generation.get() != expected) return;
        long currentDay = world.getFullTime() / 24000L;
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
            String worldName = world.getName();
            schedule(() -> dispatcher.runGlobal(plugin,
                    () -> withGlobalLimit(rule, expected, () -> dispatchPlayers(rule, expected, worldName))),
                    "生物 '" + rule.mobId() + "' 的每日刷新计数");
        }
    }

    private void withGlobalLimit(AutonomousSpawnRule rule, long expected, Runnable action) {
        if (generation.get() != expected) return;
        if (rule.maxGlobal() <= 0) {
            action.run();
            return;
        }
        collectGlobalCount(rule.mobId(), expected, count -> {
            if (count >= rule.maxGlobal()) return;
            action.run();
        });
    }

    private void collectGlobalCount(String mobId, long expected, LongConsumer callback) {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            callback.accept(0L);
            return;
        }
        AtomicLong total = new AtomicLong();
        AtomicInteger pending = new AtomicInteger(worlds.size());
        for (World world : worlds) {
            var settled = new AtomicBoolean();
            Runnable settle = () -> {
                if (!settled.compareAndSet(false, true)) return;
                if (pending.decrementAndGet() != 0) return;
                long collected = total.get();
                schedule(() -> dispatcher.runGlobal(plugin, () -> {
                    if (generation.get() != expected) return;
                    callback.accept(collected);
                }), "生物 '" + mobId + "' 的全局计数汇总");
            };
            TaskToken token = schedule(() -> dispatcher.runAtLocation(plugin, worldAnchor(world), () -> {
                total.addAndGet(countLiving(world, mobId));
                settle.run();
            }), "世界 '" + world.getName() + "' 的全局计数");
            if (token == null || token.cancelled()) {
                settle.run();
            }
        }
    }

    private void dispatchPlayers(AutonomousSpawnRule rule, long expected, String requiredWorld) {
        if (generation.get() != expected) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            schedule(() -> dispatcher.runEntity(plugin, player,
                    () -> prepareSpawn(player, rule, expected, requiredWorld)),
                    "玩家 '" + player.getName() + "' 的刷新检查");
        }
    }

    private void dispatchFollower(AutonomousSpawnRule rule, long expected) {
        if (generation.get() != expected) return;
        List<Player> candidates = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (candidates.isEmpty()) return;
        Collections.shuffle(candidates);
        AtomicBoolean claimed = new AtomicBoolean();
        for (Player player : candidates) {
            schedule(() -> dispatcher.runEntity(plugin, player, () -> {
                if (generation.get() != expected) return;
                if (!player.isOnline()) return;
                String worldName = player.getWorld().getName();
                if (!rule.worlds().isEmpty() && !rule.worlds().contains(worldName)) return;
                if (!claimed.compareAndSet(false, true)) return;
                prepareSpawn(player, rule, expected, null);
            }), "玩家 '" + player.getName() + "' 的跟随刷新");
        }
    }

    private void prepareSpawn(Player player, AutonomousSpawnRule rule, long expected, String requiredWorld) {
        if (generation.get() != expected) return;
        if (!player.isOnline()) return;
        String worldName = player.getWorld().getName();
        if (requiredWorld != null && !requiredWorld.equals(worldName)) return;
        if (!rule.worlds().isEmpty() && !rule.worlds().contains(worldName)) return;
        if (rule.condition().configured()
                && !ConditionEvaluator.evaluate(rule.condition(),
                text -> PlaceholderRenderer.renderPapi(player, text, null, "mob_spawn_condition"))) {
            return;
        }
        scheduleAttempt(player, player.getLocation(), rule, expected, 0);
    }

    private void scheduleAttempt(Player player, Location origin, AutonomousSpawnRule rule,
                                 long expected, int attempt) {
        if (generation.get() != expected) return;
        if (attempt >= 6) return;
        World world = origin.getWorld();
        if (world == null) return;
        double angle = ThreadLocalRandom.current().nextDouble() * 2D * Math.PI;
        int minDist = rule.distance().min();
        int maxDist = rule.distance().max();
        int dist = minDist >= maxDist ? minDist
                : ThreadLocalRandom.current().nextInt(minDist, maxDist + 1);
        int x = (int) (origin.getX() + Math.cos(angle) * dist);
        int z = (int) (origin.getZ() + Math.sin(angle) * dist);
        Location anchor = new Location(world, x + 0.5D, 0D, z + 0.5D);
        schedule(() -> dispatcher.runAtLocation(plugin, anchor,
                () -> evaluateCandidate(player, origin, rule, expected, attempt, anchor)),
                "生物 '" + rule.mobId() + "' 的生成位置");
    }

    private void evaluateCandidate(Player player, Location origin, AutonomousSpawnRule rule,
                                   long expected, int attempt, Location anchor) {
        if (generation.get() != expected) return;
        World world = anchor.getWorld();
        if (world == null) return;
        int x = anchor.getBlockX();
        int z = anchor.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            scheduleAttempt(player, origin, rule, expected, attempt + 1);
            return;
        }
        int topY = world.getHighestBlockYAt(x, z);
        Location candidate = new Location(world, anchor.getX(), topY + 1.0D, anchor.getZ());
        if (!isSpawnable(candidate, rule)) {
            scheduleAttempt(player, origin, rule, expected, attempt + 1);
            return;
        }
        int min = rule.count().min();
        int max = rule.count().max();
        int count = min >= max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        for (int index = 0; index < count; index++) {
            mobFactory.spawn(candidate, rule.mobId());
        }
    }

    private boolean isSpawnable(Location candidate, AutonomousSpawnRule rule) {
        int y = candidate.getBlockY();
        if (y < rule.yMin() || y > rule.yMax()) return false;
        Block block = candidate.getBlock();
        Block ground = block.getRelative(0, -1, 0);
        if (!block.isPassable() || !ground.getType().isSolid()) return false;
        if (rule.lightLevelMax() < 15
                && block.getLightLevel() > rule.lightLevelMax()) return false;
        if (!rule.biomes().isEmpty()
                && !rule.biomes().contains(block.getBiome())) return false;
        if (!rule.structures().isEmpty()
                && !isInStructures(candidate, rule.structures())) return false;
        if (!matchesTimeOfDay(candidate.getWorld(), rule.timeOfDay())) return false;
        if (rule.requireSurface()
                && block.getLightFromSky() < 15) return false;
        if (rule.maxNearby() > 0
                && countNearby(candidate, rule.mobId(), 64) >= rule.maxNearby()) return false;
        return true;
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

    private long countLiving(World world, String mobId) {
        return world.getLivingEntities().stream()
                .filter(entity -> mobId.equals(mobIdentifier.readId(entity)))
                .count();
    }

    private long countNearby(Location location, String mobId, int radius) {
        World world = location.getWorld();
        if (world == null) return 0;
        return world.getNearbyEntities(location, radius, radius, radius).stream()
                .filter(e -> e instanceof LivingEntity le && mobId.equals(mobIdentifier.readId(le)))
                .count();
    }

    private Location worldAnchor(World world) {
        return new Location(world, 0.5D, 0.0D, 0.5D);
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
