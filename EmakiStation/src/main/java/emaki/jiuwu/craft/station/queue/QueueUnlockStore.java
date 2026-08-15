package emaki.jiuwu.craft.station.queue;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;

public final class QueueUnlockStore {

    private static final String UNLOCK_FILE = "unlocks.yml";

    private final JavaPlugin plugin;
    private final Supplier<AsyncYamlFiles> files;

    public QueueUnlockStore(JavaPlugin plugin, Supplier<AsyncYamlFiles> files) {
        this.plugin = plugin;
        this.files = files;
    }

    public CompletableFuture<QueueUnlocks> loadAsync(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(null);
        }
        AsyncYamlFiles yaml = files.get();
        if (yaml == null) {
            return CompletableFuture.completedFuture(new QueueUnlocks(playerId));
        }
        File file = fileOf(playerId);
        if (!file.isFile()) {
            return CompletableFuture.completedFuture(new QueueUnlocks(playerId));
        }
        return yaml.load(file).thenApply(section -> parse(playerId, section));
    }

    public CompletableFuture<Void> saveAsync(QueueUnlocks unlocks) {
        if (unlocks == null) {
            return CompletableFuture.completedFuture(null);
        }
        AsyncYamlFiles yaml = files.get();
        if (yaml == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (unlocks.isEmpty()) {
            unlocks.clearDirty();
            return CompletableFuture.completedFuture(null);
        }
        Map<String, Object> values = serialize(unlocks);
        return yaml.save(fileOf(unlocks.playerId()), values).thenRun(unlocks::clearDirty);
    }

    private Map<String, Object> serialize(QueueUnlocks unlocks) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("player", unlocks.playerId().toString());
        Map<String, Object> stations = new LinkedHashMap<>();
        unlocks.all().forEach((stationId, slots) -> {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("purchased_slots", slots);
            stations.put(stationId, record);
        });
        root.put("stations", stations);
        return root;
    }

    private QueueUnlocks parse(UUID playerId, YamlSection section) {
        QueueUnlocks unlocks = new QueueUnlocks(playerId);
        if (section == null) {
            return unlocks;
        }
        YamlSection stations = section.getSection("stations");
        if (stations == null) {
            return unlocks;
        }
        for (String stationId : stations.getKeys(false)) {
            YamlSection station = stations.getSection(stationId);
            if (station == null) {
                continue;
            }
            unlocks.setPurchased(stationId, station.getInt("purchased_slots", 0));
        }
        unlocks.clearDirty();
        return unlocks;
    }

    private File fileOf(UUID playerId) {
        return new File(new File(new File(plugin.getDataFolder(), "data"), playerId.toString()), UNLOCK_FILE);
    }
}
