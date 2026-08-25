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

import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.accessory.persistence.AccessoryDataFile;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;

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

    private final Logger logger;
    private final AsyncYamlFiles asyncYamlFiles;
    private final AccessoryDataFile dataFile;
    private final PlayerAccessoryCache cache = new PlayerAccessoryCache();
    private final Map<UUID, PendingLoad> pendingLoads = new ConcurrentHashMap<>();
    private final Set<UUID> writeProtected = ConcurrentHashMap.newKeySet();
    private FlushResult flushResult;

    public PlayerAccessoryStore(Logger logger, AsyncYamlFiles asyncYamlFiles, AccessoryDataFile dataFile) {
        this.logger = logger;
        this.asyncYamlFiles = asyncYamlFiles;
        this.dataFile = dataFile;
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
                        warn("Failed to load accessories for " + playerId + ": "
                                + Texts.toStringSafe(throwable.getMessage()));
                        future.complete(null);
                        return;
                    }
                    PlayerAccessoryCache.CommitResult committed = cache.installLoaded(ticket, loaded);
                    future.complete(committed == PlayerAccessoryCache.CommitResult.COMMITTED ? loaded : null);
                });
        return future;
    }

    private CompletableFuture<PlayerAccessories> readAsync(UUID playerId, String playerName) {
        File file = dataFile.fileFor(playerId);
        if (asyncYamlFiles == null) {
            return CompletableFuture.completedFuture(readFile(playerId, playerName, file));
        }
        return asyncYamlFiles.read("accessory-load:" + playerId,
                () -> readFile(playerId, playerName, file));
    }

    private PlayerAccessories readFile(UUID playerId, String playerName, File file) {
        YamlSection root = file.isFile() ? YamlFiles.load(file) : null;
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
        return save(playerId, true);
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
            warn("Skipped saving accessories for " + ticket.key()
                    + " because its data file structure was not recognized");
            return CompletableFuture.completedFuture(false);
        }
        PlayerAccessories snapshot = ticket.snapshot();
        Map<String, Object> values = dataFile.write(snapshot);
        File file = dataFile.fileFor(ticket.key());
        if (asyncYamlFiles == null) {
            return CompletableFuture.completedFuture(writeFile(file, values));
        }
        return asyncYamlFiles.save(file, values)
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        warn("Failed to save accessories for " + ticket.key() + ": "
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
            warn("Failed to save accessories to " + file.getName() + ": "
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
                warn("Accessory flush did not finish before the drain deadline");
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
