package emaki.jiuwu.craft.dungeon.placeholder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.data.DomainSaveResult;
import emaki.jiuwu.craft.corelib.api.data.DomainSnapshot;
import emaki.jiuwu.craft.corelib.api.data.PlayerDataLease;
import emaki.jiuwu.craft.corelib.api.data.PlayerDomainRepository;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;

public final class DungeonRecordStore {

    public record DungeonRecord(boolean cleared,
            long firstClearMs,
            long bestTimeMs,
            long clearCount,
            double bestScore,
            Set<String> difficulties) {
    }

    private static final String CURRENT_VERSION = "1.0.0";
    private static final DungeonRecord EMPTY =
            new DungeonRecord(false, 0L, 0L, 0L, 0.0D, Set.of());
    private static final String DATABASE_DOMAIN = "dungeon_record";
    private static final int DATABASE_SCHEMA_VERSION = 1;

    private final Plugin plugin;
    private final ExecutionDispatcher dispatcher;
    private final File root;
    private final Map<UUID, Map<String, DungeonRecord>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> databaseRevisions = new ConcurrentHashMap<>();
    private final Set<UUID> databaseLeased = ConcurrentHashMap.newKeySet();
    private volatile PlayerDomainRepository databaseRepository;
    private volatile PlayerDataLease databaseLease;

    public DungeonRecordStore(Plugin plugin, ExecutionDispatcher dispatcher, File dataFolder) {
        this.plugin = plugin;
        this.dispatcher = dispatcher;
        this.root = new File(dataFolder, "records");
    }

    public void attachDatabase(PlayerDomainRepository databaseRepository, PlayerDataLease databaseLease) {
        this.databaseRepository = databaseRepository;
        this.databaseLease = databaseLease;
    }

    public void unload(UUID playerId) {
        if (playerId == null) {
            return;
        }
        databaseLeased.remove(playerId);
        cache.remove(playerId);
        PlayerDataLease lease = databaseLease;
        if (lease != null) {
            lease.release(playerId);
        }
    }

    public DungeonRecord find(UUID playerId, String dungeonId) {
        if (playerId == null || dungeonId == null) {
            return EMPTY;
        }
        Map<String, DungeonRecord> all = cache.computeIfAbsent(playerId, this::loadAll);
        if (all == null) {
            return EMPTY;
        }
        DungeonRecord record = all.get(dungeonId);
        return record == null ? EMPTY : record;
    }

    public void recordAsync(UUID playerId, String dungeonId, String difficulty,
            long durationMs, double score, long timestamp) {
        dispatcher.runAsync(plugin,
                () -> recordSync(playerId, dungeonId, difficulty, durationMs, score, timestamp));
    }

    public synchronized void recordSync(UUID playerId, String dungeonId, String difficulty,
            long durationMs, double score, long timestamp) {
        if (playerId == null || dungeonId == null) {
            return;
        }
        Map<String, DungeonRecord> loaded = cache.computeIfAbsent(playerId, this::loadAll);
        if (loaded == null) {
            return;
        }
        Map<String, DungeonRecord> all = new LinkedHashMap<>(loaded);
        DungeonRecord previous = all.getOrDefault(dungeonId, EMPTY);
        long firstClearMs = previous.firstClearMs() == 0L
                ? timestamp : Math.min(previous.firstClearMs(), timestamp);
        long bestTimeMs = previous.bestTimeMs();
        if (durationMs > 0L && (bestTimeMs == 0L || durationMs < bestTimeMs)) {
            bestTimeMs = durationMs;
        }
        double bestScore = Math.max(previous.bestScore(), score);
        Set<String> difficulties = new LinkedHashSet<>(previous.difficulties());
        if (difficulty != null && !difficulty.isBlank()) {
            difficulties.add(difficulty);
        }
        all.put(dungeonId, new DungeonRecord(true, firstClearMs, bestTimeMs,
                previous.clearCount() + 1L, bestScore, Set.copyOf(difficulties)));
        Map<String, DungeonRecord> snapshot = Map.copyOf(all);
        cache.put(playerId, snapshot);
        save(playerId, snapshot);
    }

    private Map<String, DungeonRecord> loadAll(UUID playerId) {
        Map<String, DungeonRecord> all = new LinkedHashMap<>();
        YamlSection section;
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            section = loadDatabaseSection(playerId, repository);
            if (section == null) {
                return null;
            }
        } else {
            File file = fileOf(playerId);
            if (!file.isFile()) {
                return all;
            }
            section = YamlFiles.load(file);
        }
        if (section == null || section.isEmpty()) {
            return all;
        }
        YamlSection records = section.getSection("records");
        if (records == null) {
            return all;
        }
        for (String key : records.getKeys(false)) {
            YamlSection entry = records.getSection(key);
            if (entry == null) {
                continue;
            }
            String dungeonId = entry.getString("dungeon-id", "");
            if (dungeonId.isEmpty()) {
                continue;
            }
            long firstClearMs = entry.getDouble("first-clear-ms", 0.0D).longValue();
            long bestTimeMs = entry.getDouble("best-time-ms", 0.0D).longValue();
            long clearCount = entry.getDouble("clear-count", 0.0D).longValue();
            double bestScore = entry.getDouble("best-score", 0.0D);
            Boolean clearedValue = entry.getBoolean("cleared", false);
            boolean cleared = Boolean.TRUE.equals(clearedValue) || clearCount > 0L;
            Set<String> difficulties = new LinkedHashSet<>();
            YamlSection diffSection = entry.getSection("difficulties");
            if (diffSection != null) {
                for (String diff : diffSection.getKeys(false)) {
                    Boolean value = diffSection.getBoolean(diff, false);
                    if (Boolean.TRUE.equals(value)) {
                        difficulties.add(diff);
                    }
                }
            }
            all.put(dungeonId, new DungeonRecord(cleared, firstClearMs, bestTimeMs,
                    clearCount, bestScore, Set.copyOf(difficulties)));
        }
        return all;
    }

    private void save(UUID playerId, Map<String, DungeonRecord> all) {
        Map<String, Object> values = serialize(all);
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            saveDatabase(playerId, values, repository);
            return;
        }
        File file = fileOf(playerId);
        try {
            Files.createDirectories(root.toPath());
            Path target = file.toPath();
            Path temp = target.resolveSibling(file.getName() + "." + UUID.randomUUID() + ".tmp");
            YamlFiles.save(temp.toFile(), values);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            FailureRecorder.recordIgnoredFailure("EmakiDungeon", "DungeonRecordStore.java#1", ignored);
        }
    }

    private Map<String, Object> serialize(Map<String, DungeonRecord> all) {
        Map<String, Object> recordNodes = new LinkedHashMap<>();
        List<String> ids = new ArrayList<>(all.keySet());
        for (int i = 0; i < ids.size(); i++) {
            String dungeonId = ids.get(i);
            DungeonRecord record = all.get(dungeonId);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("dungeon-id", dungeonId);
            fields.put("cleared", record.cleared());
            fields.put("first-clear-ms", record.firstClearMs());
            fields.put("best-time-ms", record.bestTimeMs());
            fields.put("clear-count", record.clearCount());
            fields.put("best-score", record.bestScore());
            Map<String, Object> diffs = new LinkedHashMap<>();
            for (String diff : record.difficulties()) {
                diffs.put(diff, true);
            }
            fields.put("difficulties", diffs);
            recordNodes.put("rec-" + (i + 1), fields);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("version", CURRENT_VERSION);
        values.put("records", recordNodes);
        return values;
    }

    private YamlSection loadDatabaseSection(UUID playerId, PlayerDomainRepository repository) {
        CompletableFuture<DomainSnapshot> future = acquireDatabaseLease(playerId)
                .thenCompose(ignored -> repository.load(playerId, DATABASE_DOMAIN));
        Throwable failure = failureOf(future);
        if (failure != null) {
            FailureRecorder.recordIgnoredFailure("EmakiDungeon", "DungeonRecordStore.java#1", failure);
            return null;
        }
        DomainSnapshot snapshot = future.getNow(null);
        if (snapshot != null) {
            databaseRevisions.put(playerId, snapshot.revision());
        }
        return decodeDatabasePayload(snapshot);
    }

    private void saveDatabase(UUID playerId, Map<String, Object> values, PlayerDomainRepository repository) {
        byte[] payload = YamlFiles.dump(values).getBytes(StandardCharsets.UTF_8);
        long expectedRevision = databaseRevisions.getOrDefault(playerId, 0L);
        CompletableFuture<DomainSaveResult> future = repository
                .save(playerId, DATABASE_DOMAIN, playerId.toString(), payload, DATABASE_SCHEMA_VERSION, expectedRevision);
        Throwable failure = failureOf(future);
        if (failure != null) {
            FailureRecorder.recordIgnoredFailure("EmakiDungeon", "DungeonRecordStore.java#1", failure);
            return;
        }
        DomainSaveResult result = future.getNow(null);
        if (result != null && result.outcome() == DomainSaveResult.Outcome.COMMITTED) {
            databaseRevisions.put(playerId, result.revision());
            return;
        }
        FailureRecorder.recordIgnoredFailure("EmakiDungeon", "DungeonRecordStore.java#1",
                new IllegalStateException("数据库保存未提交: "
                        + (result == null ? "unknown" : result.outcome())));
    }

    private YamlSection decodeDatabasePayload(DomainSnapshot snapshot) {
        if (snapshot == null || snapshot.payload().length == 0) {
            return new MapYamlSection();
        }
        return YamlFiles.load(new String(snapshot.payload(), StandardCharsets.UTF_8));
    }

    private CompletableFuture<Void> acquireDatabaseLease(UUID playerId) {
        PlayerDataLease lease = databaseLease;
        if (lease == null || databaseLeased.contains(playerId) || Bukkit.getPlayer(playerId) == null) {
            return CompletableFuture.completedFuture(null);
        }
        return attemptLease(lease, playerId, 5).thenRun(() -> databaseLeased.add(playerId));
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
                    .thenCompose(ignored -> attemptLease(lease, playerId, attemptsLeft - 1));
        });
    }

    private Throwable failureOf(CompletableFuture<?> future) {
        if (!awaitUntil(future, System.nanoTime() + TimeUnit.SECONDS.toNanos(5L))) {
            return new IllegalStateException("数据库操作超时");
        }
        if (!future.isCompletedExceptionally()) {
            return null;
        }
        try {
            future.getNow(null);
            return null;
        } catch (CompletionException | CancellationException exception) {
            return exception.getCause() == null ? exception : exception.getCause();
        }
    }

    private boolean awaitUntil(CompletableFuture<?> future, long deadline) {
        if (future.isDone()) {
            return true;
        }
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0L) {
            return false;
        }
        CountDownLatch latch = new CountDownLatch(1);
        future.whenComplete((ignored, throwable) -> latch.countDown());
        try {
            return future.isDone() || latch.await(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private File fileOf(UUID playerId) {
        return new File(root, playerId + ".yml");
    }
}
