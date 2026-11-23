package emaki.jiuwu.craft.station.queue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.api.data.DomainSaveResult;
import emaki.jiuwu.craft.corelib.api.data.DomainSnapshot;
import emaki.jiuwu.craft.corelib.api.data.PlayerDataLease;
import emaki.jiuwu.craft.corelib.api.data.PlayerDomainRepository;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.logging.Level;

public final class QueueUnlockStore {

    private static final String UNLOCK_FILE = "unlocks.yml";
    private static final String DATABASE_DOMAIN = "station_unlocks";
    private static final int DATABASE_SCHEMA_VERSION = 1;

    private final JavaPlugin plugin;
    private final Supplier<AsyncYamlFiles> files;
    private final Map<UUID, Long> databaseRevisions = new ConcurrentHashMap<>();
    private volatile PlayerDomainRepository databaseRepository;
    private volatile PlayerDataLease databaseLease;

    public QueueUnlockStore(JavaPlugin plugin, Supplier<AsyncYamlFiles> files) {
        this.plugin = plugin;
        this.files = files;
    }

    public void attachDatabase(PlayerDomainRepository databaseRepository, PlayerDataLease databaseLease) {
        this.databaseRepository = databaseRepository;
        this.databaseLease = databaseLease;
    }

    public CompletableFuture<QueueUnlocks> loadAsync(UUID playerId) {
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
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            if (unlocks.isEmpty()) {
                unlocks.clearDirty();
                return CompletableFuture.completedFuture(null);
            }
            return writeDatabaseSnapshot(unlocks, repository);
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

    public CompletableFuture<Void> unloadAsync(QueueUnlocks unlocks) {
        if (unlocks == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> saved = unlocks.dirty()
                ? saveAsync(unlocks)
                : CompletableFuture.completedFuture(null);
        PlayerDataLease lease = databaseLease;
        if (lease == null) {
            return saved;
        }
        return saved.whenComplete((_, _) -> lease.release(unlocks.playerId()));
    }

    private CompletableFuture<Void> writeDatabaseSnapshot(QueueUnlocks unlocks, PlayerDomainRepository repository) {
        UUID playerId = unlocks.playerId();
        byte[] payload = YamlFiles.dump(serialize(unlocks)).getBytes(StandardCharsets.UTF_8);
        long expectedRevision = databaseRevisions.getOrDefault(playerId, 0L);
        return repository
                .save(playerId, DATABASE_DOMAIN, null, payload, DATABASE_SCHEMA_VERSION, expectedRevision)
                .handle((result, throwable) -> {
                    if (throwable == null && result != null
                            && result.outcome() == DomainSaveResult.Outcome.COMMITTED) {
                        databaseRevisions.put(playerId, result.revision());
                        unlocks.clearDirty();
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
        LogTags.send(plugin, LogTag.PERSISTENCE, Level.WARNING, "persistence.save_player_queue_unlock_data_failed", Map.of("player", playerId), throwable);
    }

    private File fileOf(UUID playerId) {
        return new File(new File(new File(plugin.getDataFolder(), "data"), playerId.toString()), UNLOCK_FILE);
    }
}
