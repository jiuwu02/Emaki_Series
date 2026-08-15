package emaki.jiuwu.craft.mobs.spawner;

import emaki.jiuwu.craft.mobs.service.MobFactory;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.StructureSearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public final class StructureSpawnHandler implements SpawnHandler, Listener {

    private final Plugin plugin;
    private final SpawnConditionEvaluator conditionEvaluator;
    private final MobFactory mobFactory;
    private final List<StructureSpawnRule> rules = new CopyOnWriteArrayList<>();
    private final List<ScheduledTask> tasks = new ArrayList<>();

    public StructureSpawnHandler(Plugin plugin,
                                  SpawnConditionEvaluator conditionEvaluator,
                                  MobFactory mobFactory) {
        this.plugin = plugin;
        this.conditionEvaluator = conditionEvaluator;
        this.mobFactory = mobFactory;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        for (StructureSpawnRule rule : rules) {
            if (!isInStructure(event.getLocation(), rule.structures())) continue;
            if (rule.maxNearby() > 0
                    && conditionEvaluator.countNearby(event.getLocation(), rule.mobId(), 64) >= rule.maxNearby()) {
                continue;
            }
            event.setCancelled(true);
            int count = ThreadLocalRandom.current().nextInt(rule.count().min(), rule.count().max() + 1);
            for (int i = 0; i < count; i++) {
                mobFactory.spawn(event.getLocation(), rule.mobId());
            }
            return;
        }
    }

    private boolean isInStructure(Location location, List<Structure> structures) {
        for (GeneratedStructure gen : location.getChunk().getStructures()) {
            if (structures.contains(gen.getStructure())
                    && gen.getBoundingBox().contains(location.getX(), location.getY(), location.getZ())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void register(SpawnRule rule) {
        if (!(rule instanceof StructureSpawnRule r)) return;
        rules.add(r);
        if (r.activeSpawn() != null) {
            scheduleActive(r);
        }
    }

    private void scheduleActive(StructureSpawnRule rule) {
        ActiveSpawnConfig cfg = rule.activeSpawn();
        ScheduledTask task = plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> attemptActiveSpawn(rule, cfg), 1L, cfg.intervalTicks());
        tasks.add(task);
    }

    @SuppressWarnings("deprecation")
    private void attemptActiveSpawn(StructureSpawnRule rule, ActiveSpawnConfig cfg) {
        for (World world : Bukkit.getWorlds()) {
            for (Player player : world.getPlayers()) {
                for (Structure structure : rule.structures()) {
                    StructureSearchResult searchResult = world.locateNearestStructure(
                            player.getLocation(), structure.getStructureType(), 10, false);
                    if (searchResult == null) continue;
                    Location nearest = searchResult.getLocation();
                    if (cfg.requirePlayerNearby() > 0) {
                        long limitSq = (long) cfg.requirePlayerNearby() * cfg.requirePlayerNearby();
                        if (nearest.distanceSquared(player.getLocation()) > limitSq) continue;
                    }
                    if (rule.maxNearby() > 0
                            && conditionEvaluator.countNearby(nearest, rule.mobId(), 64) >= rule.maxNearby()) {
                        continue;
                    }
                    Location spawnLoc = conditionEvaluator.findSurface(nearest, 0, 16);
                    if (spawnLoc == null) continue;
                    int count = ThreadLocalRandom.current().nextInt(rule.count().min(), rule.count().max() + 1);
                    for (int i = 0; i < count; i++) {
                        mobFactory.spawn(spawnLoc, rule.mobId());
                    }
                    return;
                }
            }
        }
    }

    @Override
    public void clear() {
        tasks.forEach(ScheduledTask::cancel);
        tasks.clear();
        rules.clear();
    }
}
