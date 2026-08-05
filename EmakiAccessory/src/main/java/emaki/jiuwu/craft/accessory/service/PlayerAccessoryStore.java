package emaki.jiuwu.craft.accessory.service;

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

import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.accessory.persistence.AccessoryDataFile;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;

/**
 * IO orchestration for per-player accessory contents.
 *
 * <p>Splits responsibilities the way EmakiLevel does: {@link PlayerAccessoryCache} owns session state,
 * {@link AccessoryDataFile} owns the on-disk shape, and this class owns when reads and writes happen.
 *
 * <p>Every read waits for the player's save lane to go idle first, so a load can never observe a
 * half-written file. Futures complete on the file lane, not on a Bukkit thread: callers that touch a
 * player, inventory or GUI afterwards must hop back to the owner thread themselves and re-check the
 * generation, because the player may have reconnected in between.
 */
public final class PlayerAccessoryStore {

    /**
     * Outcome of the shutdown flush.
     *
     * @param savedEntries          how many dirty entries reached disk
     * @param failedEntries         how many dirty entries failed to write
     * @param remainingDirtyEntries how many entries were still dirty after the deadline
     * @param drainResult           the file scope drain outcome
     */
    public record FlushResult(int savedEntries,
            int failedEntries,
            int remainingDirtyEntries,
            AsyncFileService.DrainResult drainResult) {

        /** {@return whether everything was written and the file scope drained cleanly} */
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
    private FlushResult flushResult;

    /**
     * Creates the store.
     *
     * @param logger         receives IO warnings
     * @param asyncYamlFiles the module's own owner-scoped YAML lane
     * @param dataFile       the per-player file accessor
     */
    public PlayerAccessoryStore(Logger logger, AsyncYamlFiles asyncYamlFiles, AccessoryDataFile dataFile) {
        this.logger = logger;
        this.asyncYamlFiles = asyncYamlFiles;
        this.dataFile = dataFile;
    }

    /** {@return the backing session cache} */
    public PlayerAccessoryCache cache() {
        return cache;
    }

    /** {@return the per-player file accessor} */
    public AccessoryDataFile dataFile() {
        return dataFile;
    }

    /**
     * {@return the active payload for a player, or {@code null} when nothing is loaded and writable}
     *
     * @param playerId the player id
     */
    public PlayerAccessories cached(UUID playerId) {
        return cache.activeData(playerId);
    }

    /**
     * {@return the current session generation, or {@code 0} when no session exists}
     *
     * @param playerId the player id
     */
    public long currentGeneration(UUID playerId) {
        return cache.generation(playerId);
    }

    /**
     * {@return whether the given generation is still the current one}
     *
     * @param playerId   the player id
     * @param generation the generation captured earlier
     */
    public boolean isCurrentGeneration(UUID playerId, long generation) {
        return cache.isCurrentGeneration(playerId, generation);
    }

    /**
     * Applies a mutation under the entry lock, rejecting stale generations.
     *
     * @param playerId           the player id
     * @param expectedGeneration the generation the caller believes is current; {@code 0} skips the check
     * @param mutation           the mutation to apply
     * @param <R>                the mutation result type
     * @return the mutation result, or {@code null} when the session is gone, stale or not writable
     */
    public <R> R mutate(UUID playerId, long expectedGeneration, Function<PlayerAccessories, R> mutation) {
        return cache.mutate(playerId, expectedGeneration, mutation);
    }

    /**
     * Opens a session and loads the player's file.
     *
     * <p>The placeholder starts non-writable so a failed load cannot overwrite a healthy file with
     * defaults; only a successful load makes the session writable.
     *
     * @param playerId   the player id
     * @param playerName the name to record for offline admin lookups
     * @return the loaded payload, or {@code null} when the cache is sealed or the load was superseded
     */
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
        return dataFile.read(playerId, playerName, root);
    }

    /**
     * Saves a player's contents without closing the session.
     *
     * @param playerId the player id
     * @return whether a write happened and committed
     */
    public CompletableFuture<Boolean> saveAsync(UUID playerId) {
        return save(playerId, false);
    }

    /**
     * Saves a player's contents and closes the session.
     *
     * @param playerId the player id
     * @return whether a write happened and committed
     */
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

    /**
     * Saves every dirty entry.
     *
     * @return how many entries were written
     */
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

    /**
     * Flushes every dirty entry and seals the cache and file lane.
     *
     * <p>Order matters and is not interchangeable: seal the cache, drain the pending saves, and only
     * then seal the file lane. Draining first would discard writes that had not been enqueued yet.
     *
     * @param timeout total budget for the flush
     * @param unit    unit of {@code timeout}
     * @return the flush outcome; repeated calls return the first result
     */
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
