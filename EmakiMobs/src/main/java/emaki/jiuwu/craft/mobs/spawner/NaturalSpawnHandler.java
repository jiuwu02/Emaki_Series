package emaki.jiuwu.craft.mobs.spawner;

import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.mobs.service.MobFactory;
import emaki.jiuwu.craft.mobs.service.MobIdentifier;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public final class NaturalSpawnHandler implements SpawnHandler, Listener {

    private final List<NaturalSpawnRule> rules = new CopyOnWriteArrayList<>();
    private final MobIdentifier mobIdentifier;
    private final MobFactory mobFactory;

    public NaturalSpawnHandler(MobIdentifier mobIdentifier, MobFactory mobFactory) {
        this.mobIdentifier = mobIdentifier;
        this.mobFactory = mobFactory;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPreSpawn(PreCreatureSpawnEvent event) {
        rules.stream()
                .filter(r -> matchesNatural(event.getSpawnLocation(), r))
                .findFirst()
                .ifPresent(r -> {
                    if (ThreadLocalRandom.current().nextDouble() < r.replacementChance()) {
                        event.setCancelled(true);
                        event.setShouldAbortSpawn(false);
                        int count = ThreadLocalRandom.current().nextInt(r.count().min(), r.count().max() + 1);
                        for (int i = 0; i < count; i++) {
                            mobFactory.spawn(event.getSpawnLocation(), r.mobId());
                        }
                    }
                });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        SpawnReason reason = event.getSpawnReason();
        if (reason == SpawnReason.NATURAL || reason == SpawnReason.SPAWNER) return;
        rules.stream()
                .filter(r -> matchesNatural(event.getLocation(), r))
                .findFirst()
                .ifPresent(r -> {
                    if (ThreadLocalRandom.current().nextDouble() < r.replacementChance()) {
                        event.setCancelled(true);
                        int count = ThreadLocalRandom.current().nextInt(r.count().min(), r.count().max() + 1);
                        for (int i = 0; i < count; i++) {
                            mobFactory.spawn(event.getLocation(), r.mobId());
                        }
                    }
                });
    }

    private boolean matchesNatural(Location location, NaturalSpawnRule rule) {
        if (!rule.worlds().isEmpty()) {
            World world = location.getWorld();
            if (world == null || !rule.worlds().contains(world.getName())) return false;
        }
        if (!rule.biomes().isEmpty() && !rule.biomes().contains(location.getBlock().getBiome())) return false;
        int y = location.getBlockY();
        if (y < rule.yMin() || y > rule.yMax()) return false;
        if (location.getBlock().getLightLevel() > rule.lightLevelMax()) return false;
        if (rule.maxNearby() > 0 && countNearby(location, rule.mobId(), 64) >= rule.maxNearby()) return false;
        if (rule.condition().configured()) {
            return ConditionEvaluator.evaluate(rule.condition(), null);
        }
        return true;
    }

    private long countNearby(Location location, String mobId, int radius) {
        World world = location.getWorld();
        if (world == null) return 0;
        return world.getNearbyEntities(location, radius, radius, radius).stream()
                .filter(e -> e instanceof LivingEntity le && mobId.equals(mobIdentifier.readId(le)))
                .count();
    }

    @Override
    public void register(SpawnRule rule) {
        if (rule instanceof NaturalSpawnRule r) {
            rules.add(r);
        }
    }

    @Override
    public void clear() {
        rules.clear();
    }
}
