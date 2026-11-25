package emaki.jiuwu.craft.accessory.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

import java.nio.charset.StandardCharsets;

import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.accessory.persistence.AccessoryDataFile;
import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.api.data.PlayerDataLeases;
import emaki.jiuwu.craft.corelib.api.data.DomainSaveResult;
import emaki.jiuwu.craft.corelib.api.data.DomainSnapshot;
import emaki.jiuwu.craft.corelib.api.data.PlayerDataLease;
import emaki.jiuwu.craft.corelib.api.data.PlayerDomainRepository;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.logging.Level;

public final class PlayerAccessoryStore {

    public record FlushResult(int savedEntries,
            int failedEntries,
            int remainingDirtyEntries,
            AsyncFileService.DrainResult drainResult) {

        public boolean clean() {
            return failedEntries == 0
                    && remainingDirtyEntries == 0
                    && (drainResult == null || drainResult.drained());
        }
    }

    private record PendingLoad(long generation, CompletableFuture<PlayerAccessories> future) {
    }

    private static final String DATABASE_DOMAIN = "accessory";
    private static final int DATABASE_SCHEMA_VERSION = 1;

    private final Logger logger;
    private final AsyncYamlFiles asyncYamlFiles;
    private final AccessoryDataFile dataFile;
    private final PlayerAccessoryCache cache = new PlayerAccessoryCache();
    private final Map<UUID, PendingLoad> pendingLoads = new ConcurrentHashMap<>();
    private final Map<UUID, Long> databaseRevisions = new ConcurrentHashMap<>();
    private final Set<UUID> writeProtected = ConcurrentHashMap.newKeySet();
    private volatile PlayerDomainRepository databaseRepository;
    private volatile PlayerDataLease databaseLease;
    private FlushResult flushResult;

    public PlayerAccessoryStore(Logger logger, AsyncYamlFiles asyncYamlFiles, AccessoryDataFile dataFile) {
        this.logger = logger;
        this.asyncYamlFiles = asyncYamlFiles;
        this.dataFile = dataFile;
    }

    public void attachDatabase(PlayerDomainRepository databaseRepository, PlayerDataLease databaseLease) {
        this.databaseRepository = databaseRepository;
        this.databaseLease = databaseLease;
    }

    public PlayerAccessoryCache cache() {
        return cache;
    }

    public AccessoryDataFile dataFile() {
        return dataFile;
    }

    public PlayerAccessories cached(UUID playerId) {
        return cache.activeData(playerId);
    }

    public long currentGeneration(UUID playerId) {
        return cache.generation(playerId);
    }

    public boolean isCurrentGeneration(UUID playerId, long generation) {
        return cache.isCurrentGeneration(playerId, generation);
    }

    public <R> R mutate(UUID playerId, long expectedGeneration, Function<PlayerAccessories, R> mutation) {
        return cache.mutate(playerId, expectedGeneration, mutation);
    }

    public CompletableFuture<PlayerAccessories> beginSessionAsync(UUID playerId, String playerName) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(null);
        }
        PlayerAccessories placeholder = new PlayerAccessories(playerId);
        if (Texts.isNotBlank(playerName)) {
            placeholder.playerName(playerName);
            placeholder.clearDirty();
        }
        PlayerAccessoryCache.SessionTicket<UUID> ticket = cache.beginSession(playerId, placeholder, false);
        if (ticket == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<PlayerAccessories> future = new CompletableFuture<>();
        pendingLoads.put(playerId, new PendingLoad(ticket.generation(), future));
        cache.waitForIdle(playerId)
                .thenCompose(ignored -> readAsync(playerId, playerName))
                .whenComplete((loaded, throwable) -> {
                    pendingLoads.remove(playerId);
                    if (throwable != null) {
                        cache.installLoadFailure(ticket, new PlayerAccessories(playerId));
                        LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.accessory_load_failed", Map.of("player", playerId, "error", Texts.toStringSafe(throwable.getMessage())));
                        future.complete(null);
                        return;
                    }
                    PlayerAccessoryCache.CommitResult committed = cache.installLoaded(ticket, loaded);
                    future.complete(committed == PlayerAccessoryCache.CommitResult.COMMITTED ? loaded : null);
                });
        return future;
    }

    private CompletableFuture<PlayerAccessories> readAsync(UUID playerId, String playerName) {
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            return acquireDatabaseLease(playerId)
                    .thenCompose(ignored -> repository.load(playerId, DATABASE_DOMAIN))
                    .thenApply(snapshot -> {
                        if (snapshot != null) {
                            databaseRevisions.put(playerId, snapshot.revision());
                        }
                        return readDatabase(playerId, playerName, snapshot);
                    });
        }
        File file = dataFile.fileFor(playerId);
        if (asyncYamlFiles == null) {
            return CompletableFuture.completedFuture(readFile(playerId, playerName, file));
        }
        return asyncYamlFiles.read("accessory-load:" + playerId,
                () -> readFile(playerId, playerName, file));
    }

    private PlayerAccessories readDatabase(UUID playerId, String playerName, DomainSnapshot snapshot) {
        YamlSection root = snapshot == null || snapshot.payload().length == 0
                ? null
                : YamlFiles.load(new String(snapshot.payload(), StandardCharsets.UTF_8));
        return readRoot(playerId, playerName, root);
    }

    private PlayerAccessories readFile(UUID playerId, String playerName, File file) {
        YamlSection root = file.isFile() ? YamlFiles.load(file) : null;
        return readRoot(playerId, playerName, root);
    }

    private PlayerAccessories readRoot(UUID playerId, String playerName, YamlSection root) {
        if (dataFile.recognized(root)) {
            writeProtected.remove(playerId);
        } else {
            writeProtected.add(playerId);
        }
        return dataFile.read(playerId, playerName, root);
    }

    public CompletableFuture<Boolean> saveAsync(UUID playerId) {
        return save(playerId, false);
    }

    public CompletableFuture<Boolean> unloadAsync(UUID playerId) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> saved = save(playerId, true);
        PlayerDataLease lease = databaseLease;
        if (lease == null) {
            return saved;
        }
        return saved.whenComplete((result, throwable) -> lease.release(playerId));
    }

    private CompletableFuture<Boolean> save(UUID playerId, boolean closeAfterSave) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(false);
        }
        PlayerAccessoryCache.SaveTicket<UUID, PlayerAccessories> ticket =
                cache.snapshotForSave(playerId, 0L, closeAfterSave);
        if (ticket == null) {
            return CompletableFuture.completedFuture(false);
        }
        return cache.enqueueSave(ticket, this::writeTicket);
    }

    public CompletableFuture<Integer> saveAllAsync() {
        List<PlayerAccessoryCache.SaveTicket<UUID, PlayerAccessories>> tickets = cache.snapshotDirtyEntries();
        if (tickets.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        List<CompletableFuture<Boolean>> futures = saveTickets(tickets);
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    int saved = 0;
                    for (CompletableFuture<Boolean> future : futures) {
                        if (Boolean.TRUE.equals(future.getNow(false))) {
                            saved++;
                        }
                    }
                    return saved;
                });
    }

    private List<CompletableFuture<Boolean>> saveTickets(
            List<PlayerAccessoryCache.SaveTicket<UUID, PlayerAccessories>> tickets) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>(tickets.size());
        for (PlayerAccessoryCache.SaveTicket<UUID, PlayerAccessories> ticket : tickets) {
            futures.add(cache.enqueueSave(ticket, this::writeTicket));
        }
        return futures;
    }

    private CompletableFuture<Boolean> writeTicket(
            PlayerAccessoryCache.SaveTicket<UUID, PlayerAccessories> ticket) {
        if (writeProtected.contains(ticket.key())) {
            LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.accessory_save_skipped_unrecognized", Map.of("player", ticket.key()));
            return CompletableFuture.completedFuture(false);
        }
        PlayerAccessories snapshot = ticket.snapshot();
        Map<String, Object> values = dataFile.write(snapshot);
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            return writeDatabaseSnapshot(ticket.key(), snapshot, values, repository);
        }
        File file = dataFile.fileFor(ticket.key());
        if (asyncYamlFiles == null) {
            return CompletableFuture.completedFuture(writeFile(file, values));
        }
        return asyncYamlFiles.save(file, values)
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.accessory_save_failed", Map.of("player", ticket.key(), "error", Texts.toStringSafe(throwable.getMessage())));
                        return false;
                    }
                    return true;
                });
    }

    private boolean writeFile(File file, Map<String, Object> values) {
        try {
            YamlFiles.ensureDirectory(dataFile.dataRoot());
            YamlFiles.save(file, values);
            return true;
        } catch (IOException | RuntimeException exception) {
            LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.accessory_file_save_failed", Map.of("file", file.getName(), "error", Texts.toStringSafe(exception.getMessage())));
            return false;
        }
    }

    private CompletableFuture<Boolean> writeDatabaseSnapshot(UUID playerId, PlayerAccessories snapshot,
            Map<String, Object> values, PlayerDomainRepository repository) {
        byte[] payload = YamlFiles.dump(values).getBytes(StandardCharsets.UTF_8);
        long expectedRevision = databaseRevisions.getOrDefault(playerId, 0L);
        return repository
                .save(playerId, DATABASE_DOMAIN, snapshot.playerName(), payload, DATABASE_SCHEMA_VERSION, expectedRevision)
                .handle((result, throwable) -> {
                    if (throwable == null && result != null
                            && result.outcome() == DomainSaveResult.Outcome.COMMITTED) {
                        databaseRevisions.put(playerId, result.revision());
                        return true;
                    }
                    Throwable failure = throwable == null
                            ? new IllegalStateException("数据库保存未提交: "
                                    + (result == null ? "unknown" : result.outcome()))
                            : AsyncFailures.unwrapOnce(throwable);
                    LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.accessory_save_failed",
                            Map.of("player", playerId, "error", Texts.toStringSafe(failure.getMessage())));
                    return false;
                });
    }

    private CompletableFuture<Void> acquireDatabaseLease(UUID playerId) {
        PlayerDataLease lease = databaseLease;
        if (lease == null) {
            return CompletableFuture.completedFuture(null);
        }
        return PlayerDataLeases.acquireWithRetry(lease, playerId, 5);
    }


    public synchronized FlushResult flushAndSeal(long timeout, TimeUnit unit) {
        if (flushResult != null) {
            return flushResult;
        }
        Objects.requireNonNull(unit, "unit");
        long deadline = System.nanoTime() + Math.max(0L, unit.toNanos(timeout));
        cache.seal();

        List<PlayerAccessoryCache.SaveTicket<UUID, PlayerAccessories>> tickets = cache.snapshotDirtyEntries();
        List<CompletableFuture<Boolean>> futures = saveTickets(tickets);
        CompletableFuture<Void> savesComplete = futures.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        awaitUntil(savesComplete, deadline);

        int savedEntries = 0;
        for (CompletableFuture<Boolean> future : futures) {
            if (future.isDone() && !future.isCompletedExceptionally() && Boolean.TRUE.equals(future.getNow(false))) {
                savedEntries++;
            }
        }
        AsyncFileService.DrainResult drainResult;
        if (asyncYamlFiles == null) {
            int pending = (int) futures.stream().filter(future -> !future.isDone()).count();
            drainResult = new AsyncFileService.DrainResult(pending == 0, pending, List.of());
        } else {
            long remainingNanos = Math.max(0L, deadline - System.nanoTime());
            drainResult = asyncYamlFiles.sealAndDrain(remainingNanos, TimeUnit.NANOSECONDS);
        }
        flushResult = new FlushResult(savedEntries, tickets.size() - savedEntries, cache.dirtyCount(), drainResult);
        return flushResult;
    }

    private void awaitUntil(CompletableFuture<Void> future, long deadline) {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0L) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        future.whenComplete((ignored, throwable) -> latch.countDown());
        try {
            if (!latch.await(remaining, TimeUnit.NANOSECONDS)) {
                LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.accessory_write_timeout", Map.of());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void warn(String message) {
        if (logger != null) {
            logger.warning(message);
        }
    }
}
