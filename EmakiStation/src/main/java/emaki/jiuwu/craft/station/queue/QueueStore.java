package emaki.jiuwu.craft.station.queue;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.api.model.PendingOutput;
import emaki.jiuwu.craft.station.api.model.QueueEntryState;

/**
 * Reads and writes {@code data/<uuid>/queue.yml}.
 *
 * <p>All file work goes through CoreLib's owner-scoped async YAML service, which serialises operations per
 * file so a save and a load can never interleave on the same player.
 *
 * <h2>The crash window this does not close</h2>
 * A queue file and the warehouse are two independent files with independent flush timing. A hard crash can
 * therefore leave them disagreeing in one of two directions: materials debited but the entry unsaved (the
 * player loses materials), or the entry saved but the debit unflushed (the player gets a free craft).
 *
 * <p>Three things reduce the exposure without pretending to eliminate it: a successful submission flushes
 * its file immediately rather than waiting for autosave, the autosave interval is shorter than the
 * warehouse's, and every entry carries a full consumed-material receipt so a manual reconciliation is
 * actually possible. Closing the window properly would need a cross-plugin two-phase commit, which costs
 * far more than it is worth here.
 */
public final class QueueStore {

    private static final String QUEUE_FILE = "queue.yml";

    private final JavaPlugin plugin;
    private final Supplier<AsyncYamlFiles> files;

    /**
     * Creates the store.
     *
     * @param plugin the owning plugin, used to resolve the data directory
     * @param files  a supplier of the owner-scoped YAML service, re-read on each call so a CoreLib reload
     *               does not leave a stale scope behind
     */
    public QueueStore(JavaPlugin plugin, Supplier<AsyncYamlFiles> files) {
        this.plugin = plugin;
        this.files = files;
    }

    /**
     * Loads one player's queues.
     *
     * <p><strong>Thread:</strong> any thread. The future carries no completion-thread guarantee, so the
     * caller applies the result on the owner thread itself.
     *
     * @param playerId the owner
     * @return a future carrying the loaded queues; empty when the player has no file
     */
    public CompletableFuture<PlayerQueues> loadAsync(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(null);
        }
        AsyncYamlFiles yaml = files.get();
        if (yaml == null) {
            return CompletableFuture.completedFuture(new PlayerQueues(playerId));
        }
        File file = fileOf(playerId);
        if (!file.isFile()) {
            return CompletableFuture.completedFuture(new PlayerQueues(playerId));
        }
        return yaml.load(file).thenApply(section -> parse(playerId, section));
    }

    /**
     * Saves one player's queues, deleting the file when nothing is left to store.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param queues the queues to persist
     * @return a future completing once the write finishes
     */
    public CompletableFuture<Void> saveAsync(PlayerQueues queues) {
        if (queues == null) {
            return CompletableFuture.completedFuture(null);
        }
        AsyncYamlFiles yaml = files.get();
        if (yaml == null) {
            return CompletableFuture.completedFuture(null);
        }
        queues.pruneEmpty();
        File file = fileOf(queues.playerId());
        if (queues.isEmpty()) {
            return yaml.read("station-queue-delete", () -> {
                deleteQuietly(file);
                return null;
            }).thenAccept(ignored -> queues.clearDirty());
        }
        Map<String, Object> values = serialize(queues);
        return yaml.save(file, values).thenRun(queues::clearDirty);
    }

    private Map<String, Object> serialize(PlayerQueues queues) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("player", queues.playerId().toString());
        Map<String, Object> stations = new LinkedHashMap<>();
        for (CraftQueue queue : queues.all()) {
            if (queue.isEmpty()) {
                continue;
            }
            List<Map<String, Object>> entries = new ArrayList<>();
            for (QueueEntry entry : queue.entries()) {
                entries.add(serializeEntry(entry));
            }
            stations.put(queue.stationId(), entries);
        }
        root.put("stations", stations);
        return root;
    }

    private Map<String, Object> serializeEntry(QueueEntry entry) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("recipe", entry.recipeId());
        values.put("batch", entry.batch());
        values.put("channel", entry.channel().token());
        values.put("state", entry.state().token());
        values.put("duration_ms", entry.durationMillis());
        values.put("started_at_ms", entry.startedAtMs());
        values.put("accumulated_ms", entry.accumulatedMs());
        values.put("last_tick_ms", entry.lastTickMs());
        List<Map<String, Object>> consumed = new ArrayList<>();
        for (ConsumedMaterial material : entry.consumedMaterials()) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("source", ItemSourceUtil.toShorthand(material.source()));
            record.put("amount", material.amount());
            record.put("channel", material.channel().token());
            consumed.add(record);
        }
        values.put("consumed", consumed);
        // Written unconditionally so a file always states the charge explicitly; a file from before currency
        // costs existed simply has no key and parses as "charged nothing", which is correct for it.
        values.put("cost_provider", entry.costProviderId());
        values.put("cost_amount", entry.costAmount());
        List<Map<String, Object>> pending = new ArrayList<>();
        for (PendingOutput output : entry.pendingOutputs()) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("source", ItemSourceUtil.toShorthand(output.source()));
            record.put("amount", output.amount());
            pending.add(record);
        }
        values.put("pending_outputs", pending);
        return values;
    }

    private PlayerQueues parse(UUID playerId, YamlSection section) {
        PlayerQueues queues = new PlayerQueues(playerId);
        if (section == null) {
            return queues;
        }
        YamlSection stations = section.getSection("stations");
        if (stations == null) {
            return queues;
        }
        for (String stationId : stations.getKeys(false)) {
            CraftQueue queue = queues.queue(stationId);
            for (Map<?, ?> raw : stations.getMapList(stationId)) {
                QueueEntry entry = parseEntry(raw);
                if (entry != null) {
                    queue.add(entry);
                }
            }
        }
        queues.pruneEmpty();
        return queues;
    }

    private QueueEntry parseEntry(Map<?, ?> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<String, Object> values = normalizeKeys(raw);
        String recipeId = asString(values.get("recipe"));
        if (recipeId == null || recipeId.isBlank()) {
            return null;
        }
        List<ConsumedMaterial> consumed = new ArrayList<>();
        for (Object element : asList(values.get("consumed"))) {
            if (element instanceof Map<?, ?> record) {
                Map<String, Object> fields = normalizeKeys(record);
                ItemSourceRef source = ItemSourceUtil.parse(asString(fields.get("source")));
                long amount = asLong(fields.get("amount"), 0L);
                if (source != null && amount > 0L) {
                    consumed.add(new ConsumedMaterial(source, amount,
                            MaterialChannel.parse(asString(fields.get("channel")), MaterialChannel.BACKPACK)));
                }
            }
        }
        QueueEntry entry = new QueueEntry(recipeId,
                asLong(values.get("batch"), 1L),
                MaterialChannel.parse(asString(values.get("channel")), MaterialChannel.BACKPACK),
                asLong(values.get("duration_ms"), 0L),
                consumed,
                QueueEntryState.parse(asString(values.get("state")), QueueEntryState.WAITING),
                asLong(values.get("started_at_ms"), 0L),
                asLong(values.get("accumulated_ms"), 0L),
                0L,
                asString(values.get("cost_provider")),
                asLong(values.get("cost_amount"), 0L));
        List<PendingOutput> pending = new ArrayList<>();
        for (Object element : asList(values.get("pending_outputs"))) {
            if (element instanceof Map<?, ?> record) {
                Map<String, Object> fields = normalizeKeys(record);
                ItemSourceRef source = ItemSourceUtil.parse(asString(fields.get("source")));
                long amount = asLong(fields.get("amount"), 0L);
                if (source != null && amount > 0L) {
                    pending.add(new PendingOutput(source, amount));
                }
            }
        }
        if (entry.state() == QueueEntryState.PENDING_CLAIM) {
            entry.markPendingClaim(pending);
        }
        return entry;
    }

    private File fileOf(UUID playerId) {
        return new File(new File(new File(plugin.getDataFolder(), "data"), playerId.toString()), QUEUE_FILE);
    }

    private void deleteQuietly(File file) {
        if (!file.isFile()) {
            return;
        }
        if (!file.delete()) {
            plugin.getLogger().warning("Could not delete stale queue file: " + file.getPath());
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && parent.isDirectory()) {
            String[] remaining = parent.list();
            if (remaining != null && remaining.length == 0) {
                parent.delete();
            }
        }
    }

    private static Map<String, Object> normalizeKeys(Map<?, ?> raw) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return normalized;
    }

    private static List<?> asList(Object raw) {
        return raw instanceof List<?> list ? list : List.of();
    }

    private static String asString(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }

    private static long asLong(Object raw, long fallback) {
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
