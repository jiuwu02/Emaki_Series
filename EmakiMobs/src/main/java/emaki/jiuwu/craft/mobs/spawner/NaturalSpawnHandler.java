package emaki.jiuwu.craft.mobs.spawner;

import com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent;
import emaki.jiuwu.craft.mobs.service.MobFactory;
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
    private final SpawnConditionEvaluator conditionEvaluator;
    private final MobFactory mobFactory;

    public NaturalSpawnHandler(SpawnConditionEvaluator conditionEvaluator, MobFactory mobFactory) {
        this.conditionEvaluator = conditionEvaluator;
        this.mobFactory = mobFactory;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPreSpawn(PreCreatureSpawnEvent event) {
        rules.stream()
                .filter(r -> conditionEvaluator.matchesNatural(event.getSpawnLocation(), r))
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
                .filter(r -> conditionEvaluator.matchesNatural(event.getLocation(), r))
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
