package emaki.jiuwu.craft.station.queue;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.api.model.PendingOutput;
import emaki.jiuwu.craft.station.api.model.QueueEntryState;

public final class QueueStore {

    private static final String QUEUE_FILE = "queue.yml";

    private final JavaPlugin plugin;
    private final Supplier<AsyncYamlFiles> files;

    public QueueStore(JavaPlugin plugin, Supplier<AsyncYamlFiles> files) {
        this.plugin = plugin;
        this.files = files;
    }

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
        root.put("schema", 2);
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
        values.put("schema", entry.schemaVersion());
        values.put("recipe_identity", entry.recipeIdentity());
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
            record.put("material_id", material.materialId());
            record.put("requirement_id", material.requirementId());
            record.put("count_key", material.countKey());
            record.put("matched_source", ItemSourceUtil.toShorthand(material.source()));
            record.put("source", ItemSourceUtil.toShorthand(material.source()));
            record.put("position", material.position());
            record.put("amount", material.amount());
            record.put("refunded_amount", material.refundedAmount());
            if (material.itemSnapshot() != null) {
                record.put("item_snapshot", Base64.getEncoder().encodeToString(
                        material.itemSnapshot().serializeAsBytes()));
            }
            record.put("channel", material.channel().token());
            consumed.add(record);
        }
        values.put("consumed", consumed);

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

    static Map<String, Object> migrateEntry(Map<?, ?> raw) {
        return LegacyQueueIdentityMigration.migrate(normalizeKeys(raw));
    }

    private QueueEntry parseEntry(Map<?, ?> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        Map<String, Object> values = migrateEntry(raw);
        if (values.isEmpty()) {
            return null;
        }
        String recipeId = asString(values.get("recipe"));
        if (recipeId == null || recipeId.isBlank()) {
            return null;
        }
        List<ConsumedMaterial> consumed = new ArrayList<>();
        for (Object element : asList(values.get("consumed"))) {
            if (element instanceof Map<?, ?> record) {
                Map<String, Object> fields = normalizeKeys(record);
                String sourceToken = asString(fields.get("matched_source"));
                if (sourceToken == null || sourceToken.isBlank()) {
                    sourceToken = asString(fields.get("source"));
                }
                ItemSourceRef source = ItemSourceUtil.parse(sourceToken);
                long amount = asLong(fields.get("amount"), 0L);
                if (source != null && amount > 0L) {
                    consumed.add(new ConsumedMaterial(
                            defaultIdentity(asString(fields.get("material_id"))),
                            defaultIdentity(asString(fields.get("requirement_id"))),
                            defaultIdentity(asString(fields.get("count_key"))),
                            source,
                            (int) asLong(fields.get("position"), -1L),
                            MaterialChannel.parse(asString(fields.get("channel")), MaterialChannel.BACKPACK),
                            amount,
                            asLong(fields.get("refunded_amount"), 0L),
                            deserializeItem(asString(fields.get("item_snapshot")))));
                }
            }
        }
        QueueEntry entry = new QueueEntry((int) asLong(values.get("schema"), 1L),
                defaultRecipeIdentity(asString(values.get("recipe_identity")), recipeId),
                recipeId,
                asLong(values.get("batch"), 1L),
                MaterialChannel.parse(asString(values.get("channel")), MaterialChannel.BACKPACK),
                asLong(values.get("duration_ms"), 0L),
                consumed,
                QueueEntryState.parse(asString(values.get("state")), QueueEntryState.WAITING),
                asLong(values.get("started_at_ms"), 0L),
                asLong(values.get("accumulated_ms"), 0L),
                asLong(values.get("last_tick_ms"), 0L),
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

    private static ItemStack deserializeItem(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException failure) {
            return null;
        }
    }

    private static String defaultIdentity(String value) {
        return value == null || value.isBlank() ? "legacy" : value;
    }

    private static String defaultRecipeIdentity(String value, String recipeId) {
        return value == null || value.isBlank() ? recipeId : value;
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
