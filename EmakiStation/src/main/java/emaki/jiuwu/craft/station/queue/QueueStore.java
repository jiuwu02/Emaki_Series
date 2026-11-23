package emaki.jiuwu.craft.station.queue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.api.data.DomainSaveResult;
import emaki.jiuwu.craft.corelib.api.data.DomainSnapshot;
import emaki.jiuwu.craft.corelib.api.data.PlayerDataLease;
import emaki.jiuwu.craft.corelib.api.data.PlayerDomainRepository;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.api.model.PendingOutput;
import emaki.jiuwu.craft.station.api.model.QueueEntryState;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.logging.Level;

public final class QueueStore {

    private static final String QUEUE_FILE = "queue.yml";
    private static final String DATABASE_DOMAIN = "station_queue";
    private static final int DATABASE_SCHEMA_VERSION = 1;

    private final JavaPlugin plugin;
    private final Supplier<AsyncYamlFiles> files;
    private final Map<UUID, Long> databaseRevisions = new ConcurrentHashMap<>();
    private volatile PlayerDomainRepository databaseRepository;
    private volatile PlayerDataLease databaseLease;

    public QueueStore(JavaPlugin plugin, Supplier<AsyncYamlFiles> files) {
        this.plugin = plugin;
        this.files = files;
    }

    public void attachDatabase(PlayerDomainRepository databaseRepository, PlayerDataLease databaseLease) {
        this.databaseRepository = databaseRepository;
        this.databaseLease = databaseLease;
    }

    public CompletableFuture<PlayerQueues> loadAsync(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(null);
        }
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            return acquireDatabaseLease(playerId)
                    .thenCompose(_ -> repository.load(playerId, DATABASE_DOMAIN))
                    .thenApply(snapshot -> {
                        if (snapshot != null) {
                            databaseRevisions.put(playerId, snapshot.revision());
                        }
                        return parse(playerId, decodeDatabasePayload(snapshot));
                    });
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
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            queues.pruneEmpty();
            return writeDatabaseSnapshot(queues, repository);
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

    public CompletableFuture<Void> unloadAsync(PlayerQueues queues) {
        if (queues == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> saved = saveAsync(queues);
        PlayerDataLease lease = databaseLease;
        if (lease == null) {
            return saved;
        }
        return saved.whenComplete((_, _) -> lease.release(queues.playerId()));
    }

    private CompletableFuture<Void> writeDatabaseSnapshot(PlayerQueues queues, PlayerDomainRepository repository) {
        UUID playerId = queues.playerId();
        byte[] payload = queues.isEmpty()
                ? new byte[0]
                : YamlFiles.dump(serialize(queues)).getBytes(StandardCharsets.UTF_8);
        long expectedRevision = databaseRevisions.getOrDefault(playerId, 0L);
        return repository
                .save(playerId, DATABASE_DOMAIN, null, payload, DATABASE_SCHEMA_VERSION, expectedRevision)
                .handle((result, throwable) -> {
                    if (throwable == null && result != null
                            && result.outcome() == DomainSaveResult.Outcome.COMMITTED) {
                        databaseRevisions.put(playerId, result.revision());
                        queues.clearDirty();
                        return (Void) null;
                    }
                    Throwable failure = throwable == null
                            ? new IllegalStateException("数据库保存未提交: "
                                    + (result == null ? "unknown" : result.outcome()))
                            : AsyncFailures.unwrap(throwable);
                    logSaveFailure(playerId, failure);
                    throw new CompletionException(failure);
                });
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
            LogTags.send(plugin, LogTag.PERSISTENCE, Level.WARNING, "persistence.delete_expired_queue_file_failed", Map.of("path", file.getPath()));
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

    private YamlSection decodeDatabasePayload(DomainSnapshot snapshot) {
        if (snapshot == null || snapshot.payload().length == 0) {
            return new MapYamlSection();
        }
        return YamlFiles.load(new String(snapshot.payload(), StandardCharsets.UTF_8));
    }

    private CompletableFuture<Void> acquireDatabaseLease(UUID playerId) {
        PlayerDataLease lease = databaseLease;
        if (lease == null) {
            return CompletableFuture.completedFuture(null);
        }
        return attemptLease(lease, playerId, 5);
    }

    private CompletableFuture<Void> attemptLease(PlayerDataLease lease, UUID playerId, int attemptsLeft) {
        return lease.acquire(playerId).thenCompose(acquired -> {
            if (Boolean.TRUE.equals(acquired)) {
                return CompletableFuture.completedFuture(null);
            }
            if (attemptsLeft <= 1) {
                return CompletableFuture.failedFuture(new IllegalStateException("数据租约获取失败: " + playerId));
            }
            return CompletableFuture.runAsync(() -> { },
                            CompletableFuture.delayedExecutor(2L, TimeUnit.SECONDS))
                    .thenCompose(_ -> attemptLease(lease, playerId, attemptsLeft - 1));
        });
    }

    private void logSaveFailure(UUID playerId, Throwable throwable) {
        LogTags.send(plugin, LogTag.PERSISTENCE, Level.WARNING, "persistence.save_player_queue_data_failed", Map.of("player", playerId), throwable);
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
