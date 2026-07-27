package emaki.jiuwu.craft.storage.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.SortMode;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;
import emaki.jiuwu.craft.storage.persistence.StorageDataFile;
import emaki.jiuwu.craft.storage.persistence.StorageMetaFile;

/**
 * Owns loading, saving and unloading of player storages.
 *
 * <p>Reads and writes run on the module's owner-scoped async file lane; only the hand-off back
 * into live state happens on the owning entity thread, which the caller arranges. Saves are
 * chained per player by {@link PlayerStorageCache}, so a save can never overtake an earlier one
 * or a pending load of the same file.
 */
public final class PlayerStorageStore {

    /**
     * Outcome of the shutdown flush.
     *
     * @param savedEntries          how many storages were written
     * @param failedEntries         how many writes failed
     * @param remainingDirtyEntries how many storages were still dirty when the timeout expired
     * @param drainResult           the file scope drain outcome
     */
    public record FlushResult(int savedEntries,
            int failedEntries,
            int remainingDirtyEntries,
            AsyncFileService.DrainResult drainResult) {

        public boolean clean() {
            return failedEntries == 0
                    && remainingDirtyEntries == 0
                    && drainResult != null
                    && drainResult.drained()
                    && drainResult.failures().isEmpty();
        }
    }

    private record PendingLoad(long generation, CompletableFuture<PlayerStorage> future) {
    }

    private final Logger logger;
    private final AsyncFileService.FileScope fileScope;
    private final StorageDataFile dataFile;
    private final StorageMetaFile metaFile;
    private final StorageTextIndexer textIndexer;
    private final PlayerStorageCache cache = new PlayerStorageCache();
    private final Map<UUID, PendingLoad> pendingLoads = new ConcurrentHashMap<>();

    private volatile SortMode defaultSortMode = SortMode.AMOUNT_DESC;
    private volatile boolean defaultAutoPickup;
    private volatile int warnEntryCount;
    private FlushResult flushResult;

    /**
     * @param logger      the plugin logger
     * @param fileScope   the module's owner-scoped async file lane
     * @param dataRoot    {@code plugins/EmakiStorage/data}
     * @param corruptRoot {@code plugins/EmakiStorage/corrupt}
     * @param textIndexer builds the pre-computed search and sort text
     */
    public PlayerStorageStore(Logger logger,
            AsyncFileService.FileScope fileScope,
            Path dataRoot,
            Path corruptRoot,
            StorageTextIndexer textIndexer) {
        this.logger = logger;
        this.fileScope = fileScope;
        this.dataFile = new StorageDataFile(dataRoot, corruptRoot);
        this.metaFile = new StorageMetaFile(dataRoot);
        this.textIndexer = textIndexer;
    }

    /** Applies reloaded settings that affect loading defaults. */
    public void configure(SortMode defaultSortMode, int warnEntryCount) {
        configure(defaultSortMode, warnEntryCount, defaultAutoPickup);
    }

    /**
     * Applies reloaded settings that affect loading defaults.
     *
     * @param defaultSortMode   sort mode for players without a stored value
     * @param warnEntryCount    entry count that triggers a size warning
     * @param defaultAutoPickup auto pickup state for players without a stored value
     */
    public void configure(SortMode defaultSortMode, int warnEntryCount, boolean defaultAutoPickup) {
        this.defaultSortMode = defaultSortMode == null ? SortMode.AMOUNT_DESC : defaultSortMode;
        this.warnEntryCount = Math.max(0, warnEntryCount);
        this.defaultAutoPickup = defaultAutoPickup;
    }

    public PlayerStorageCache cache() {
        return cache;
    }

    /** {@return the live storage for a player, or {@code null} when not loaded} */
    public PlayerStorage cached(UUID playerId) {
        return cache.active(playerId);
    }

    public long currentGeneration(UUID playerId) {
        return cache.generation(playerId);
    }

    public boolean isCurrentGeneration(UUID playerId, long generation) {
        return cache.isCurrentGeneration(playerId, generation);
    }

    public boolean writable(UUID playerId) {
        return cache.writable(playerId);
    }

    /**
     * Begins a session and loads the player's data from disk.
     *
     * <p>The returned future completes on a file-lane thread. Callers that need to touch Bukkit
     * state must hop to the owning entity thread themselves and re-check the generation, because
     * the player may have reconnected while the read was in flight.
     *
     * @param playerId   the storage owner
     * @param playerName the last known name, recorded for troubleshooting only
     * @return a future completing with the loaded storage, or {@code null} when superseded
     */
    public CompletableFuture<PlayerStorage> beginSession(UUID playerId, String playerName) {
        PlayerStorage placeholder = new PlayerStorage(playerId);
        placeholder.playerName(playerName);
        placeholder.sortMode(defaultSortMode);
        PlayerStorageCache.SessionTicket ticket = cache.beginSession(playerId, placeholder, false);
        if (ticket == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<PlayerStorage> future = new CompletableFuture<>();
        pendingLoads.put(playerId, new PendingLoad(ticket.generation(), future));
        cache.waitForIdle(playerId)
                .thenCompose(ignored -> fileScope.read(dataFile.dataFile(playerId),
                        "storage-load", () -> readStorage(playerId, playerName)))
                .whenComplete((loaded, throwable) -> {
                    pendingLoads.remove(playerId);
                    if (throwable != null) {
                        PlayerStorage fallback = new PlayerStorage(playerId);
                        fallback.playerName(playerName);
                        fallback.sortMode(defaultSortMode);
                        cache.installLoadFailure(ticket, fallback);
                        logFailure("load", playerId, unwrap(throwable));
                        future.complete(null);
                        return;
                    }
                    PlayerStorageCache.CommitResult result = cache.installLoaded(ticket, loaded);
                    future.complete(result == PlayerStorageCache.CommitResult.COMMITTED ? loaded : null);
                });
        return future;
    }

    /**
     * Reads one player's storage from disk. Runs on a file-lane thread.
     *
     * @param playerId   the storage owner
     * @param playerName the last known name
     * @return the reconstructed storage
     */
    private PlayerStorage readStorage(UUID playerId, String playerName) {
        PlayerStorage storage = new PlayerStorage(playerId);
        storage.playerName(playerName);
        StorageMetaFile.Meta meta = metaFile.load(playerId, defaultSortMode, defaultAutoPickup);
        storage.grantedSlots(meta.grantedSlots());
        storage.purchasedSlots(meta.purchasedSlots());
        storage.defaultStackLimit(meta.defaultStackLimit());
        storage.sortMode(meta.sortMode());
        storage.autoPickupEnabled(meta.autoPickupEnabled());
        if (playerName == null || playerName.isBlank()) {
            storage.playerName(meta.playerName());
        }

        StorageDataFile.LoadResult loaded;
        try {
            loaded = dataFile.load(playerId);
        } catch (IOException failure) {
            throw new CompletionException(failure);
        }
        if (loaded.hasCorruption()) {
            logger.warning("[storage] Quarantined " + loaded.corruptRecords()
                    + " unreadable record(s) for " + playerId
                    + (loaded.quarantineTarget() == null
                            ? "" : " into " + loaded.quarantineTarget().getFileName()));
        }
        int merged = 0;
        for (StorageDataFile.Record record : loaded.records()) {
            StorageKey key = StorageKey.of(record.template());
            StorageEntry existing = storage.entry(key);
            if (existing != null) {
                // Two records that now compare equal, e.g. after a component format change.
                // Amounts are summed; discarding either one would silently destroy player items.
                existing.add(record.amount(), 0L);
                merged++;
                continue;
            }
            storage.append(textIndexer.createEntry(key, record.amount(), record.stackLimit()));
        }
        if (merged > 0) {
            logger.warning("[storage] Merged " + merged + " duplicate entr(ies) for " + playerId
                    + "; two stored items now compare equal after a component format change.");
        }
        storage.pruneEmpty();
        storage.clearDirty();
        warnOnLargeStorage(playerId, storage);
        return storage;
    }

    private void warnOnLargeStorage(UUID playerId, PlayerStorage storage) {
        int threshold = warnEntryCount;
        if (threshold > 0 && storage.entryCount() > threshold) {
            logger.warning("[storage] Player " + playerId + " holds " + storage.entryCount()
                    + " entries, above capacity.warn_entry_count=" + threshold + ".");
        }
    }

    /**
     * Saves a player without unloading.
     *
     * @param playerId the storage owner
     * @return a future completing with whether anything was written
     */
    public CompletableFuture<Boolean> saveAsync(UUID playerId) {
        PlayerStorageCache.SaveTicket ticket = cache.snapshotForSave(playerId, false);
        if (ticket == null) {
            return CompletableFuture.completedFuture(false);
        }
        return cache.enqueueSave(ticket, this::writeTicket);
    }

    /**
     * Saves and unloads a player.
     *
     * @param playerId the storage owner
     * @return a future completing with whether anything was written
     */
    public CompletableFuture<Boolean> unloadAsync(UUID playerId) {
        PlayerStorageCache.SaveTicket ticket = cache.snapshotForSave(playerId, true);
        if (ticket == null) {
            return CompletableFuture.completedFuture(false);
        }
        return cache.enqueueSave(ticket, this::writeTicket);
    }

    /** Saves every dirty storage without unloading. */
    public CompletableFuture<Integer> saveAllAsync() {
        List<PlayerStorageCache.SaveTicket> tickets = cache.snapshotDirtyEntries();
        if (tickets.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        List<CompletableFuture<Boolean>> saves = new ArrayList<>(tickets.size());
        for (PlayerStorageCache.SaveTicket ticket : tickets) {
            saves.add(cache.enqueueSave(ticket, this::writeTicket));
        }
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

    private CompletableFuture<Boolean> writeTicket(PlayerStorageCache.SaveTicket ticket) {
        PlayerStorage snapshot = ticket.snapshot();
        UUID playerId = ticket.playerId();
        List<StorageDataFile.Record> records = new ArrayList<>(snapshot.entryCount());
        for (StorageKey key : snapshot.entryOrder()) {
            StorageEntry entry = snapshot.entry(key);
            if (entry == null || entry.empty()) {
                continue;
            }
            records.add(new StorageDataFile.Record(key.toItemStack(), entry.amount(), entry.stackLimit()));
        }
        StorageMetaFile.Meta meta = new StorageMetaFile.Meta(snapshot.playerName(),
                snapshot.grantedSlots(), snapshot.purchasedSlots(),
                snapshot.defaultStackLimit(), snapshot.sortMode(), snapshot.autoPickupEnabled());
        return fileScope.write(dataFile.dataFile(playerId), "storage-save", () -> {
            try {
                dataFile.save(playerId, records);
                metaFile.save(playerId, meta);
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }).handle((ignored, throwable) -> {
            if (throwable != null) {
                logFailure("save", playerId, unwrap(throwable));
                return false;
            }
            return true;
        });
    }

    /**
     * Seals the cache, flushes everything and drains the file lane.
     *
     * <p>Idempotent: repeated calls return the first result.
     *
     * @param timeout how long to wait in total
     * @param unit    the timeout unit
     * @return the flush outcome
     */
    public synchronized FlushResult flushAndSeal(long timeout, TimeUnit unit) {
        if (flushResult != null) {
            return flushResult;
        }
        long deadline = System.nanoTime() + Math.max(0L, unit.toNanos(timeout));
        cache.seal();

        List<PlayerStorageCache.SaveTicket> tickets = cache.snapshotDirtyEntries();
        List<CompletableFuture<Boolean>> saves = new ArrayList<>(tickets.size());
        for (PlayerStorageCache.SaveTicket ticket : tickets) {
            saves.add(cache.enqueueSave(ticket, this::writeTicket));
        }
        CompletableFuture<Void> all = saves.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
        awaitUntil(all, deadline);

        int saved = 0;
        for (CompletableFuture<Boolean> save : saves) {
            if (save.isDone() && !save.isCompletedExceptionally() && Boolean.TRUE.equals(save.getNow(false))) {
                saved++;
            }
        }
        long remainingNanos = Math.max(0L, deadline - System.nanoTime());
        AsyncFileService.DrainResult drain = fileScope.sealAndDrain(remainingNanos, TimeUnit.NANOSECONDS);
        flushResult = new FlushResult(saved, tickets.size() - saved, cache.dirtyCount(), drain);
        cache.clear();
        return flushResult;
    }

    private void awaitUntil(CompletableFuture<?> future, long deadlineNanos) {
        if (future.isDone()) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        future.whenComplete((ignored, throwable) -> latch.countDown());
        long remaining = deadlineNanos - System.nanoTime();
        if (remaining <= 0L) {
            return;
        }
        try {
            boolean ignored = latch.await(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void logFailure(String stage, UUID playerId, Throwable throwable) {
        logger.log(Level.WARNING, "[storage] Failed to " + stage + " data for " + playerId
                + ": " + describe(throwable));
    }

    private static String describe(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : " " + message);
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
