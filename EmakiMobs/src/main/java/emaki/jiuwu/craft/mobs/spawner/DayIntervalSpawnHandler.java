package emaki.jiuwu.craft.mobs.spawner;

import emaki.jiuwu.craft.mobs.service.MobFactory;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public final class DayIntervalSpawnHandler implements SpawnHandler {

    private final Plugin plugin;
    private final SpawnConditionEvaluator conditionEvaluator;
    private final MobFactory mobFactory;
    private final List<DayIntervalSpawnRule> rules = new CopyOnWriteArrayList<>();
    private final ScheduledTask dayTickerTask;

    public DayIntervalSpawnHandler(Plugin plugin,
                                   SpawnConditionEvaluator conditionEvaluator,
                                   MobFactory mobFactory) {
        this.plugin = plugin;
        this.conditionEvaluator = conditionEvaluator;
        this.mobFactory = mobFactory;
        this.dayTickerTask = plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> checkAllRules(), 1L, 20L);
    }

    private void checkAllRules() {
        for (World world : Bukkit.getWorlds()) {
            for (DayIntervalSpawnRule rule : rules) {
                checkDayTick(world, rule);
            }
        }
    }

    private void checkDayTick(World world, DayIntervalSpawnRule rule) {
        long fullTime = world.getFullTime();
        long today = fullTime / 24000L;
        if (rule.onDayStart() && fullTime % 24000L != 0) return;
        long lastDay = readLastSpawnDay(world, rule.mobId());
        if (today - lastDay < rule.intervalDays()) return;
        saveLastSpawnDay(world, rule.mobId(), today);
        triggerSpawnNearPlayers(world, rule);
    }

    private void triggerSpawnNearPlayers(World world, DayIntervalSpawnRule rule) {
        for (Player player : world.getPlayers()) {
            if (rule.maxGlobal() > 0 && conditionEvaluator.countGlobal(rule.mobId()) >= rule.maxGlobal()) return;
            Location loc = conditionEvaluator.findSurface(
                    player.getLocation(), rule.distanceFromPlayer().min(), rule.distanceFromPlayer().max());
            if (loc == null) continue;
            int count = ThreadLocalRandom.current().nextInt(rule.count().min(), rule.count().max() + 1);
            for (int i = 0; i < count; i++) {
                mobFactory.spawn(loc, rule.mobId());
            }
        }
    }

    private NamespacedKey dayKey(String mobId) {
        return new NamespacedKey(plugin, "last_spawn_day_" + mobId.replace(":", "_").replace("/", "_"));
    }

    private long readLastSpawnDay(World world, String mobId) {
        Long val = world.getPersistentDataContainer().get(dayKey(mobId), PersistentDataType.LONG);
        return val == null ? -999L : val;
    }

    private void saveLastSpawnDay(World world, String mobId, long day) {
        world.getPersistentDataContainer().set(dayKey(mobId), PersistentDataType.LONG, day);
    }

    @Override
    public void register(SpawnRule rule) {
        if (rule instanceof DayIntervalSpawnRule r) {
            rules.add(r);
        }
    }

    @Override
    public void clear() {
        rules.clear();
    }
}
