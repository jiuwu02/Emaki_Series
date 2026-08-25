package emaki.jiuwu.craft.codex.codex.service;

import java.io.File;
import java.io.IOException;
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
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;

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

    private final Logger logger;
    private final AsyncYamlFiles asyncYamlFiles;
    private final CodexDataFile dataFile;
    private final PlayerCodexCache cache = new PlayerCodexCache();
    private final Map<UUID, PendingLoad> pendingLoads = new ConcurrentHashMap<>();
    private FlushResult flushResult;

    public PlayerCodexStore(Logger logger, AsyncYamlFiles asyncYamlFiles, CodexDataFile dataFile) {
        this.logger = logger;
        this.asyncYamlFiles = asyncYamlFiles;
        this.dataFile = dataFile;
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
                        warn("Failed to load codex progress for " + playerId + ": "
                                + Texts.toStringSafe(throwable.getMessage()));
                        future.complete(null);
                        return;
                    }
                    PlayerCodexCache.CommitResult committed = cache.installLoaded(ticket, loaded);
                    future.complete(committed == PlayerCodexCache.CommitResult.COMMITTED ? loaded : null);
                });
        return future;
    }

    private CompletableFuture<PlayerCodex> readAsync(UUID playerId, String playerName) {
        File file = dataFile.fileFor(playerId);
        if (asyncYamlFiles == null) {
            return CompletableFuture.completedFuture(readFile(playerId, playerName, file));
        }
        return asyncYamlFiles.read("codex-load:" + playerId,
                () -> readFile(playerId, playerName, file));
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
        if (ticket == null) {
            return CompletableFuture.completedFuture(false);
        }
        return cache.enqueueSave(ticket, this::writeTicket);
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
        Map<String, Object> values = dataFile.write(ticket.snapshot());
        File file = dataFile.fileFor(ticket.key());
        if (asyncYamlFiles == null) {
            return CompletableFuture.completedFuture(writeFile(file, values));
        }
        return asyncYamlFiles.save(file, values)
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        warn("Failed to save codex progress for " + ticket.key() + ": "
                                + Texts.toStringSafe(throwable.getMessage()));
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
            warn("Failed to save codex progress to " + file.getName() + ": "
                    + Texts.toStringSafe(exception.getMessage()));
            return false;
        }
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
                warn("Codex flush did not finish before the drain deadline");
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
