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

public final class PlayerRelativeSpawnHandler implements SpawnHandler {

    private final Plugin plugin;
    private final SpawnConditionEvaluator conditionEvaluator;
    private final MobFactory mobFactory;
    private final List<ScheduledTask> tasks = new ArrayList<>();

    public PlayerRelativeSpawnHandler(Plugin plugin,
                                      SpawnConditionEvaluator conditionEvaluator,
                                      MobFactory mobFactory) {
        this.plugin = plugin;
        this.conditionEvaluator = conditionEvaluator;
        this.mobFactory = mobFactory;
    }

    @Override
    public void register(SpawnRule rule) {
        if (!(rule instanceof PlayerRelativeSpawnRule r)) return;
        ScheduledTask task = plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> attemptSpawn(r), 1L, r.intervalTicks());
        tasks.add(task);
    }

    private void attemptSpawn(PlayerRelativeSpawnRule rule) {
        if (rule.maxGlobal() > 0 && conditionEvaluator.countGlobal(rule.mobId()) >= rule.maxGlobal()) return;
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) return;
        Player player = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        Location loc = conditionEvaluator.findSurface(
                player.getLocation(), rule.distance().min(), rule.distance().max());
        if (loc == null) return;
        if (rule.requireSkyAccess() && loc.getBlock().getLightFromSky() < 15) return;
        int count = ThreadLocalRandom.current().nextInt(rule.count().min(), rule.count().max() + 1);
        for (int i = 0; i < count; i++) {
            mobFactory.spawn(loc, rule.mobId());
        }
    }

    @Override
    public void clear() {
        tasks.forEach(ScheduledTask::cancel);
        tasks.clear();
    }
}
