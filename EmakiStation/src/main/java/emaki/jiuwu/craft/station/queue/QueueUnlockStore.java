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

/**
 * Reads and writes {@code data/<uuid>/unlocks.yml}.
 *
 * <h2>Why this file is never deleted</h2>
 * {@link QueueStore} deletes {@code queue.yml} when a player's queues are empty, which keeps the data
 * directory from filling with files that say nothing. That rule must not reach purchased queue slots: a player
 * who bought capacity and then let their queue drain would lose what they paid for.
 *
 * <p>So this store only ever writes. An empty record is skipped rather than turned into a delete, and there is
 * deliberately no delete path at all — not even a guarded one, because the guard is the kind of thing a later
 * change quietly inverts.
 *
 * <p>All file work goes through CoreLib's owner-scoped async YAML service, which serialises operations per file
 * so a save and a load can never interleave on the same player.
 */
public final class QueueUnlockStore {

    private static final String UNLOCK_FILE = "unlocks.yml";

    private final JavaPlugin plugin;
    private final Supplier<AsyncYamlFiles> files;

    /**
     * Creates the store.
     *
     * @param plugin the owning plugin, used to resolve the data directory
     * @param files  a supplier of the owner-scoped YAML service, re-read on each call so a CoreLib reload does
     *               not leave a stale scope behind
     */
    public QueueUnlockStore(JavaPlugin plugin, Supplier<AsyncYamlFiles> files) {
        this.plugin = plugin;
        this.files = files;
    }

    /**
     * Loads one player's purchased slots.
     *
     * <p><strong>Thread:</strong> any thread. The future carries no completion-thread guarantee, so the caller
     * applies the result on the owner thread itself.
     *
     * @param playerId the owner
     * @return a future carrying the loaded record; empty when the player has no file
     */
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

    /**
     * Saves one player's purchased slots.
     *
     * <p>A record with nothing in it is not written and not deleted; there is simply nothing to do.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param unlocks the record to persist
     * @return a future completing once the write finishes
     */
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
