package emaki.jiuwu.craft.mobs.spawner;

import emaki.jiuwu.craft.mobs.service.MobFactory;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class BiomeSpawnHandler implements SpawnHandler {

    private final Plugin plugin;
    private final SpawnConditionEvaluator conditionEvaluator;
    private final MobFactory mobFactory;
    private final List<ScheduledTask> tasks = new ArrayList<>();

    public BiomeSpawnHandler(Plugin plugin,
                              SpawnConditionEvaluator conditionEvaluator,
                              MobFactory mobFactory) {
        this.plugin = plugin;
        this.conditionEvaluator = conditionEvaluator;
        this.mobFactory = mobFactory;
    }

    @Override
    public void register(SpawnRule rule) {
        if (!(rule instanceof BiomeSpawnRule r)) return;
        ScheduledTask task = plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> spawnForRule(r), 1L, r.intervalTicks());
        tasks.add(task);
    }

    private void spawnForRule(BiomeSpawnRule rule) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location candidate = conditionEvaluator.findSurface(
                    player.getLocation(), rule.distance().min(), rule.distance().max());
            if (candidate == null) continue;
            if (!conditionEvaluator.matchesBiomes(candidate, rule.biomes())) continue;
            if (rule.conditions() != null
                    && !conditionEvaluator.matchesConditions(candidate, rule.conditions())) {
                continue;
            }
            if (rule.maxNearby() > 0
                    && conditionEvaluator.countNearby(candidate, rule.mobId(), 32) >= rule.maxNearby()) {
                continue;
            }
            int count = ThreadLocalRandom.current().nextInt(rule.count().min(), rule.count().max() + 1);
            for (int i = 0; i < count; i++) {
                mobFactory.spawn(candidate, rule.mobId());
            }
        }
    }

    @Override
    public void clear() {
        tasks.forEach(ScheduledTask::cancel);
        tasks.clear();
    }
}
