package emaki.jiuwu.craft.codex.codex.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;

import emaki.jiuwu.craft.codex.codex.model.PlayerCodex;
import emaki.jiuwu.craft.codex.codex.persistence.CodexDataFile;
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

public final class PlayerCodexStore {

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

    private record PendingLoad(long generation, CompletableFuture<PlayerCodex> future) {
    }

    private static final String DATABASE_DOMAIN = "codex";
    private static final int DATABASE_SCHEMA_VERSION = 1;

    private final Logger logger;
    private final AsyncYamlFiles asyncYamlFiles;
    private final CodexDataFile dataFile;
    private final PlayerCodexCache cache = new PlayerCodexCache();
    private final Map<UUID, PendingLoad> pendingLoads = new ConcurrentHashMap<>();
    private final Map<UUID, Long> databaseRevisions = new ConcurrentHashMap<>();
    private volatile PlayerDomainRepository databaseRepository;
    private volatile PlayerDataLease databaseLease;
    private FlushResult flushResult;

    public PlayerCodexStore(Logger logger, AsyncYamlFiles asyncYamlFiles, CodexDataFile dataFile) {
        this.logger = logger;
        this.asyncYamlFiles = asyncYamlFiles;
        this.dataFile = dataFile;
    }

    public void attachDatabase(PlayerDomainRepository databaseRepository, PlayerDataLease databaseLease) {
        this.databaseRepository = databaseRepository;
        this.databaseLease = databaseLease;
    }

    public PlayerCodexCache cache() {
        return cache;
    }

    public CodexDataFile dataFile() {
        return dataFile;
    }

    public PlayerCodex cached(UUID playerId) {
        return cache.activeData(playerId);
    }

    public long currentGeneration(UUID playerId) {
        return cache.generation(playerId);
    }

    public boolean isCurrentGeneration(UUID playerId, long generation) {
        return cache.isCurrentGeneration(playerId, generation);
    }

    public <R> R mutate(UUID playerId, long expectedGeneration, Function<PlayerCodex, R> mutation) {
        return cache.mutate(playerId, expectedGeneration, mutation);
    }

    public CompletableFuture<PlayerCodex> beginSessionAsync(UUID playerId, String playerName) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(null);
        }
        PlayerCodex placeholder = new PlayerCodex(playerId);
        if (Texts.isNotBlank(playerName)) {
            placeholder.playerName(playerName);
            placeholder.clearDirty();
        }
        PlayerCodexCache.SessionTicket<UUID> ticket = cache.beginSession(playerId, placeholder, false);
        if (ticket == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<PlayerCodex> future = new CompletableFuture<>();
        pendingLoads.put(playerId, new PendingLoad(ticket.generation(), future));
        cache.waitForIdle(playerId)
                .thenCompose(ignored -> readAsync(playerId, playerName))
                .whenComplete((loaded, throwable) -> {
                    pendingLoads.remove(playerId);
                    if (throwable != null) {
                        cache.installLoadFailure(ticket, new PlayerCodex(playerId));
                        LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.load_progress_failed", Map.of("player", playerId, "error", Texts.toStringSafe(throwable.getMessage())));
                        future.complete(null);
                        return;
                    }
                    PlayerCodexCache.CommitResult committed = cache.installLoaded(ticket, loaded);
                    future.complete(committed == PlayerCodexCache.CommitResult.COMMITTED ? loaded : null);
                });
        return future;
    }

    private CompletableFuture<PlayerCodex> readAsync(UUID playerId, String playerName) {
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            return readDatabase(playerId, playerName, repository);
        }
        File file = dataFile.fileFor(playerId);
        if (asyncYamlFiles == null) {
            return CompletableFuture.completedFuture(readFile(playerId, playerName, file));
        }
        return asyncYamlFiles.read("codex-load:" + playerId,
                () -> readFile(playerId, playerName, file));
    }

    private CompletableFuture<PlayerCodex> readDatabase(UUID playerId, String playerName,
            PlayerDomainRepository repository) {
        return acquireDatabaseLease(playerId)
                .thenCompose(ignored -> repository.load(playerId, DATABASE_DOMAIN))
                .thenApply(snapshot -> {
                    if (snapshot != null) {
                        databaseRevisions.put(playerId, snapshot.revision());
                    }
                    return dataFile.read(playerId, playerName, decodeDatabasePayload(snapshot));
                });
    }

    private YamlSection decodeDatabasePayload(DomainSnapshot snapshot) {
        if (snapshot == null || snapshot.payload().length == 0) {
            return null;
        }
        return YamlFiles.load(new String(snapshot.payload(), StandardCharsets.UTF_8));
    }

    private PlayerCodex readFile(UUID playerId, String playerName, File file) {
        YamlSection root = file.isFile() ? YamlFiles.load(file) : null;
        return dataFile.read(playerId, playerName, root);
    }

    public CompletableFuture<Boolean> saveAsync(UUID playerId) {
        return save(playerId, false);
    }

    public CompletableFuture<Boolean> unloadAsync(UUID playerId) {
        return save(playerId, true);
    }

    private CompletableFuture<Boolean> save(UUID playerId, boolean closeAfterSave) {
        if (playerId == null) {
            return CompletableFuture.completedFuture(false);
        }
        PlayerCodexCache.SaveTicket<UUID, PlayerCodex> ticket =
                cache.snapshotForSave(playerId, 0L, closeAfterSave);
        CompletableFuture<Boolean> saved = ticket == null
                ? CompletableFuture.completedFuture(false)
                : cache.enqueueSave(ticket, this::writeTicket);
        if (!closeAfterSave) {
            return saved;
        }
        PlayerDataLease lease = databaseLease;
        if (lease == null) {
            return saved;
        }
        return saved.whenComplete((ignored, throwable) -> lease.release(playerId));
    }

    public CompletableFuture<Integer> saveAllAsync() {
        List<PlayerCodexCache.SaveTicket<UUID, PlayerCodex>> tickets = cache.snapshotDirtyEntries();
        if (tickets.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        List<CompletableFuture<Boolean>> saves = saveTickets(tickets);
        return CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    int saved = 0;
                    for (CompletableFuture<Boolean> save : saves) {
                        if (Boolean.TRUE.equals(save.getNow(false))) {
                            saved++;
                        }
                    }
                    return saved;
                });
    }

    private List<CompletableFuture<Boolean>> saveTickets(
            List<PlayerCodexCache.SaveTicket<UUID, PlayerCodex>> tickets) {
        List<CompletableFuture<Boolean>> saves = new ArrayList<>(tickets.size());
        for (PlayerCodexCache.SaveTicket<UUID, PlayerCodex> ticket : tickets) {
            saves.add(cache.enqueueSave(ticket, this::writeTicket));
        }
        return saves;
    }

    private CompletableFuture<Boolean> writeTicket(PlayerCodexCache.SaveTicket<UUID, PlayerCodex> ticket) {
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            return writeDatabaseTicket(ticket, repository);
        }
        Map<String, Object> values = dataFile.write(ticket.snapshot());
        File file = dataFile.fileFor(ticket.key());
        if (asyncYamlFiles == null) {
            return CompletableFuture.completedFuture(writeFile(file, values));
        }
        return asyncYamlFiles.save(file, values)
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.save_progress_failed", Map.of("player", ticket.key(), "error", Texts.toStringSafe(throwable.getMessage())));
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
            LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.save_progress_to_file_failed", Map.of("file", file.getName(), "error", Texts.toStringSafe(exception.getMessage())));
            return false;
        }
    }

    private CompletableFuture<Boolean> writeDatabaseTicket(PlayerCodexCache.SaveTicket<UUID, PlayerCodex> ticket,
            PlayerDomainRepository repository) {
        PlayerCodex snapshot = ticket.snapshot();
        byte[] payload = YamlFiles.dump(dataFile.write(snapshot)).getBytes(StandardCharsets.UTF_8);
        long expectedRevision = databaseRevisions.getOrDefault(ticket.key(), 0L);
        return repository
                .save(ticket.key(), DATABASE_DOMAIN, snapshot.playerName(), payload, DATABASE_SCHEMA_VERSION,
                        expectedRevision)
                .handle((result, throwable) -> {
                    if (throwable == null && result != null
                            && result.outcome() == DomainSaveResult.Outcome.COMMITTED) {
                        databaseRevisions.put(ticket.key(), result.revision());
                        return true;
                    }
                    LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.save_progress_failed",
                            Map.of("player", ticket.key(), "error", Texts.toStringSafe(throwable == null
                                    ? "数据库保存未提交: "
                                            + (result == null ? "unknown" : result.outcome())
                                    : throwable.getMessage())));
                    return false;
                });
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
                    .thenCompose(ignored -> attemptLease(lease, playerId, attemptsLeft - 1));
        });
    }

    public synchronized FlushResult flushAndSeal(long timeout, TimeUnit unit) {
        if (flushResult != null) {
            return flushResult;
        }
        Objects.requireNonNull(unit, "unit");
        long deadline = System.nanoTime() + Math.max(0L, unit.toNanos(timeout));
        cache.seal();

        List<PlayerCodexCache.SaveTicket<UUID, PlayerCodex>> tickets = cache.snapshotDirtyEntries();
        List<CompletableFuture<Boolean>> saves = saveTickets(tickets);
        CompletableFuture<Void> savesComplete = saves.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
        awaitUntil(savesComplete, deadline);

        int savedEntries = 0;
        for (CompletableFuture<Boolean> save : saves) {
            if (save.isDone() && !save.isCompletedExceptionally() && Boolean.TRUE.equals(save.getNow(false))) {
                savedEntries++;
            }
        }
        AsyncFileService.DrainResult drainResult;
        if (asyncYamlFiles == null) {
            int pending = (int) saves.stream().filter(save -> !save.isDone()).count();
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
                LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.flush_missed_drain_deadline", Map.of());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

}
