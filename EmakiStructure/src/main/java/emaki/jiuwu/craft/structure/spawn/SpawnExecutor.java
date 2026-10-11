package emaki.jiuwu.craft.structure.spawn;

import emaki.jiuwu.craft.structure.EmakiStructurePlugin;
import emaki.jiuwu.craft.structure.config.AppConfig;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class SpawnExecutor {

    private static final String MYTHIC_PLUGIN = "MythicMobs";
    private static final String EMAKI_MOBS_PLUGIN = "EmakiMobs";
    private static final String PREFIX_MINECRAFT = "minecraft:";
    private static final String PREFIX_MYTHIC = "mythicmobs:";
    private static final String PREFIX_EMAKI_MOBS = "emakimobs:";

    private final EmakiStructurePlugin plugin;
    private final SpawnQueue queue;
    private final Supplier<AppConfig.EntityPolicy> entityPolicy;
    private final Supplier<Boolean> respectEntityPolicy;

    private MythicSpawnBridge mythicBridge;
    private EmakiMobsSpawnBridge emakiMobsBridge;

    public SpawnExecutor(EmakiStructurePlugin plugin,
            SpawnQueue queue,
            Supplier<AppConfig.EntityPolicy> entityPolicy,
            Supplier<Boolean> respectEntityPolicy) {
        this.plugin = plugin;
        this.queue = queue;
        this.entityPolicy = entityPolicy;
        this.respectEntityPolicy = respectEntityPolicy;
    }

    public int schedule(SpawnContext context, SpawnEntry entry, int entryIndex, String templateId) {
        AppConfig.EntityPolicy policy = entityPolicy.get();
        if (policy == AppConfig.EntityPolicy.NONE) {
            return 0;
        }
        if (entry.chance() < 1.0d && ThreadLocalRandom.current().nextDouble() >= entry.chance()) {
            return 0;
        }
        boolean conservative = respectEntityPolicy.get();
        boolean passive = policy == AppConfig.EntityPolicy.PASSIVE;
        int scheduled = 0;
        for (int count = 0; count < entry.count(); count++) {
            Location location = SpawnPointSampler.sample(context, entry);
            location.setYaw(SpawnPointSampler.combinedYaw(context, entry));
            queue.submit(location, () -> spawnOne(context, entry, entryIndex, templateId,
                    location, passive, conservative));
            scheduled++;
        }
        return scheduled;
    }

    private void spawnOne(SpawnContext context, SpawnEntry entry, int entryIndex, String templateId,
            Location location, boolean passive, boolean conservative) {
        SpawnOutcome outcome = dispatch(location, entry, templateId, entryIndex, conservative);
        if (!outcome.spawned()) {
            plugin.messageService().warning("console.spawn_entry_failed", Map.of(
                    "template", templateId,
                    "index", entryIndex,
                    "type", entry.type(),
                    "error", String.valueOf(outcome.errorDetail())));
            return;
        }
        LivingEntity spawned = outcome.entity();
        if (passive && shouldFilter(entry, spawned, conservative)) {
            spawned.remove();
        }
    }

    private boolean shouldFilter(SpawnEntry entry, LivingEntity spawned, boolean conservative) {
        if (entry.type().startsWith(PREFIX_MINECRAFT)) {
            return VanillaSpawnBridge.isHostile(spawned);
        }
        return conservative;
    }

    private SpawnOutcome dispatch(Location location, SpawnEntry entry, String templateId,
            int entryIndex, boolean conservative) {
        String type = entry.type();
        if (type.startsWith(PREFIX_MYTHIC)) {
            if (!pluginPresent(MYTHIC_PLUGIN)) {
                return SpawnOutcome.failed("error.spawn.entry_failed", "MythicMobs 未安装");
            }
            try {
                if (mythicBridge == null) {
                    mythicBridge = new MythicSpawnBridge();
                }
                return mythicBridge.spawn(location, type.substring(PREFIX_MYTHIC.length()), entry.level());
            } catch (LinkageError error) {
                return SpawnOutcome.failed("error.spawn.entry_failed", String.valueOf(error.getMessage()));
            }
        }
        if (type.startsWith(PREFIX_EMAKI_MOBS)) {
            if (!pluginPresent(EMAKI_MOBS_PLUGIN)) {
                return SpawnOutcome.failed("error.spawn.entry_failed", "EmakiMobs 未安装");
            }
            try {
                if (emakiMobsBridge == null) {
                    emakiMobsBridge = new EmakiMobsSpawnBridge();
                }
                return emakiMobsBridge.spawn(location, type.substring(PREFIX_EMAKI_MOBS.length()));
            } catch (LinkageError error) {
                return SpawnOutcome.failed("error.spawn.entry_failed", String.valueOf(error.getMessage()));
            }
        }
        return VanillaSpawnBridge.spawn(location, type);
    }

    private boolean pluginPresent(String pluginName) {
        return plugin.getServer().getPluginManager().getPlugin(pluginName) != null;
    }
}
