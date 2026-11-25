package emaki.jiuwu.craft.storage.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.api.data.PlayerDataLeases;
import emaki.jiuwu.craft.corelib.api.async.AsyncWaits;
import emaki.jiuwu.craft.corelib.api.data.DomainSaveResult;
import emaki.jiuwu.craft.corelib.api.data.DomainSnapshot;
import emaki.jiuwu.craft.corelib.api.data.PlayerDataLease;
import emaki.jiuwu.craft.corelib.api.data.PlayerDomainRepository;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.SortMode;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;
import emaki.jiuwu.craft.storage.model.StorageReservation;
import emaki.jiuwu.craft.storage.persistence.StorageDataFile;
import emaki.jiuwu.craft.storage.persistence.StorageMetaFile;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;

public final class PlayerStorageStore {

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

    private static final String DATABASE_DOMAIN = "storage";
    private static final int DATABASE_SCHEMA_VERSION = 1;

    private final Logger logger;
    private final AsyncFileService.FileScope fileScope;
    private final StorageDataFile dataFile;
    private final StorageMetaFile metaFile;
    private final StorageTextIndexer textIndexer;
    private final PlayerStorageCache cache = new PlayerStorageCache();
    private final Map<UUID, PendingLoad> pendingLoads = new ConcurrentHashMap<>();
    private final Map<UUID, Long> databaseRevisions = new ConcurrentHashMap<>();
    private volatile PlayerDomainRepository databaseRepository;
    private volatile PlayerDataLease databaseLease;

    private volatile SortMode defaultSortMode = SortMode.AMOUNT_DESC;
    private volatile boolean defaultAutoPickup;
    private volatile int warnEntryCount;
    private FlushResult flushResult;

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

    public void attachDatabase(PlayerDomainRepository databaseRepository, PlayerDataLease databaseLease) {
        this.databaseRepository = databaseRepository;
        this.databaseLease = databaseLease;
    }

    public void configure(SortMode defaultSortMode, int warnEntryCount) {
        configure(defaultSortMode, warnEntryCount, defaultAutoPickup);
    }

    public void configure(SortMode defaultSortMode, int warnEntryCount, boolean defaultAutoPickup) {
        this.defaultSortMode = defaultSortMode == null ? SortMode.AMOUNT_DESC : defaultSortMode;
        this.warnEntryCount = Math.max(0, warnEntryCount);
        this.defaultAutoPickup = defaultAutoPickup;
    }

    public PlayerStorageCache cache() {
        return cache;
    }

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

    public CompletableFuture<PlayerStorage> beginSessionAsync(UUID playerId, String playerName) {
        PlayerStorage placeholder = new PlayerStorage(playerId);
        placeholder.playerName(playerName);
        placeholder.sortMode(defaultSortMode);
        PlayerStorageCache.SessionTicket ticket = cache.beginSession(playerId, placeholder, false);
        if (ticket == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<PlayerStorage> future = new CompletableFuture<>();
        pendingLoads.put(playerId, new PendingLoad(ticket.generation(), future));
        CompletableFuture<PlayerStorage> physicalLoad;
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            physicalLoad = acquireDatabaseLease(playerId)
                    .thenCompose(ignored -> repository.load(playerId, DATABASE_DOMAIN))
                    .thenApply(snapshot -> {
                        if (snapshot != null) {
                            databaseRevisions.put(playerId, snapshot.revision());
                        }
                        return readDatabase(playerId, playerName, snapshot);
                    });
        } else {
            physicalLoad = cache.waitForIdleAsync(playerId)
                    .thenCompose(ignored -> fileScope.read(dataFile.dataFile(playerId),
                            "storage-load", () -> readStorage(playerId, playerName)));
        }
        physicalLoad.whenComplete((loaded, throwable) -> {
            pendingLoads.remove(playerId);
            if (throwable != null) {
                PlayerStorage fallback = new PlayerStorage(playerId);
                fallback.playerName(playerName);
                fallback.sortMode(defaultSortMode);
                cache.installLoadFailure(ticket, fallback);
                logFailure("load", playerId, AsyncFailures.unwrap(throwable));
                future.complete(null);
                return;
            }
            PlayerStorageCache.CommitResult result = cache.installLoaded(ticket, loaded);
            future.complete(result == PlayerStorageCache.CommitResult.COMMITTED ? loaded : null);
        });
        return future;
    }

    private PlayerStorage readStorage(UUID playerId, String playerName) {
        StorageMetaFile.Meta meta = metaFile.load(playerId, defaultSortMode, defaultAutoPickup);
        StorageDataFile.LoadResult loaded;
        try {
            loaded = dataFile.load(playerId);
        } catch (IOException failure) {
            throw new CompletionException(failure);
        }
        return assemble(playerId, playerName, meta, loaded);
    }

    private PlayerStorage readDatabase(UUID playerId, String playerName, DomainSnapshot snapshot) {
        StorageMetaFile.Meta meta = StorageMetaFile.Meta.defaults(defaultSortMode, defaultAutoPickup);
        StorageDataFile.LoadResult loaded = StorageDataFile.LoadResult.empty();
        byte[] payload = snapshot == null ? null : snapshot.payload();
        if (payload != null && payload.length > 0) {
            YamlSection section = YamlFiles.load(new String(payload, StandardCharsets.UTF_8));
            meta = StorageMetaFile.fromSection(section, defaultSortMode, defaultAutoPickup);
            String encoded = section.getString("data", "");
            if (encoded != null && !encoded.isBlank()) {
                try {
                    loaded = dataFile.decode(playerId, Base64.getDecoder().decode(encoded.trim()));
                } catch (IOException failure) {
                    throw new IllegalStateException("仓库数据解码失败: " + playerId, failure);
                }
            }
        }
        return assemble(playerId, playerName, meta, loaded);
    }

    private PlayerStorage assemble(UUID playerId, String playerName,
            StorageMetaFile.Meta meta, StorageDataFile.LoadResult loaded) {
        PlayerStorage storage = new PlayerStorage(playerId);
        storage.playerName(playerName);
        storage.grantedSlots(meta.grantedSlots());
        storage.purchasedSlots(meta.purchasedSlots());
        storage.defaultStackLimit(meta.defaultStackLimit());
        storage.sortMode(meta.sortMode());
        storage.autoPickupEnabled(meta.autoPickupEnabled());
        if (playerName == null || playerName.isBlank()) {
            storage.playerName(meta.playerName());
        }
        if (loaded.hasCorruption()) {
            LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.quarantine_unreadable_records", Map.of("player", playerId, "count", loaded.corruptRecords(), "quarantine_target", (loaded.quarantineTarget() == null
                            ? "" : ", 已移入 " + loaded.quarantineTarget().getFileName())));
        }
        if (loaded.truncated()) {
            LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.storage_file_truncated", Map.of("player", playerId, "detail", loaded.truncationDetail()));
            throw new CompletionException(new IOException("存储文件框架被截断: " + loaded.truncationDetail()));
        }
        int merged = 0;
        for (StorageDataFile.Record record : loaded.records()) {
            StorageKey key = StorageKey.of(record.template());
            StorageEntry existing = storage.entry(key);
            if (existing != null) {

                existing.add(record.amount(), 0L);
                merged++;
                continue;
            }
            storage.append(textIndexer.createEntry(key, record.amount(), record.stackLimit()));
        }
        if (merged > 0) {
            LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.duplicate_entries_merged", Map.of("player", playerId, "count", merged));
        }
        restoreReservations(playerId, storage, loaded.reservations());
        storage.pruneEmpty();
        storage.clearDirty();
        warnOnLargeStorage(playerId, storage);
        return storage;
    }

    private void restoreReservations(UUID playerId, PlayerStorage storage,
            List<StorageDataFile.ReservationRecord> reservations) {
        if (reservations.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int expired = 0;
        for (StorageDataFile.ReservationRecord record : reservations) {
            if (now >= record.expiresAtMillis()) {
                expired++;
                continue;
            }
            List<StorageReservation.Op> ops = new ArrayList<>(record.ops().size());
            for (StorageDataFile.ReservationOpRecord op : record.ops()) {
                ops.add(new StorageReservation.Op(StorageKey.of(op.template()), op.delta()));
            }
            storage.addReservation(new StorageReservation(record.reservationId(),
                    record.expiresAtMillis(), ops));
        }
        if (expired > 0) {
            LogTags.send(logger, LogTag.PERSISTENCE, Level.INFO, "persistence.expired_reservations_released", Map.of("player", playerId, "count", expired));
        }
    }

    private void warnOnLargeStorage(UUID playerId, PlayerStorage storage) {
        int threshold = warnEntryCount;
        if (threshold > 0 && storage.entryCount() > threshold) {
            LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.storage_entry_count_warn", Map.of("player", playerId, "count", storage.entryCount(), "limit", threshold));
        }
    }

    public CompletableFuture<Boolean> saveAsync(UUID playerId) {
        PlayerStorageCache.SaveTicket ticket = cache.snapshotForSave(playerId, false);
        if (ticket == null) {
            return CompletableFuture.completedFuture(false);
        }
        return cache.enqueueSaveAsync(ticket, this::writeTicket);
    }

    public CompletableFuture<Boolean> unloadAsync(UUID playerId) {
        PlayerStorageCache.SaveTicket ticket = cache.snapshotForSave(playerId, true);
        PlayerDataLease lease = databaseLease;
        if (ticket == null) {
            if (lease != null) {
                lease.release(playerId);
            }
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> saved = cache.enqueueSaveAsync(ticket, this::writeTicket);
        if (lease == null) {
            return saved;
        }
        return saved.whenComplete((result, throwable) -> lease.release(playerId));
    }

    public CompletableFuture<Integer> saveAllAsync() {
        List<PlayerStorageCache.SaveTicket> tickets = cache.snapshotDirtyEntries();
        if (tickets.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        List<CompletableFuture<Boolean>> saves = new ArrayList<>(tickets.size());
        for (PlayerStorageCache.SaveTicket ticket : tickets) {
            saves.add(cache.enqueueSaveAsync(ticket, this::writeTicket));
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
        List<StorageDataFile.ReservationRecord> reservations =
                new ArrayList<>(snapshot.reservations().size());
        for (StorageReservation reservation : snapshot.reservations().values()) {
            List<StorageDataFile.ReservationOpRecord> ops = new ArrayList<>(reservation.ops().size());
            for (StorageReservation.Op op : reservation.ops()) {
                ops.add(new StorageDataFile.ReservationOpRecord(op.key().toItemStack(), op.delta()));
            }
            reservations.add(new StorageDataFile.ReservationRecord(reservation.reservationId(),
                    reservation.expiresAtMillis(), ops));
        }
        StorageMetaFile.Meta meta = new StorageMetaFile.Meta(snapshot.playerName(),
                snapshot.grantedSlots(), snapshot.purchasedSlots(),
                snapshot.defaultStackLimit(), snapshot.sortMode(), snapshot.autoPickupEnabled());
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            return writeDatabaseSnapshot(playerId, snapshot, records, reservations, meta, repository);
        }
        return fileScope.write(dataFile.dataFile(playerId), "storage-save", () -> {
            try {
                dataFile.save(playerId, records, reservations);
                metaFile.save(playerId, meta);
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }).handle((ignored, throwable) -> {
            if (throwable != null) {
                logFailure("save", playerId, AsyncFailures.unwrap(throwable));
                return false;
            }
            return true;
        });
    }

    private CompletableFuture<Boolean> writeDatabaseSnapshot(UUID playerId, PlayerStorage snapshot,
            List<StorageDataFile.Record> records, List<StorageDataFile.ReservationRecord> reservations,
            StorageMetaFile.Meta meta, PlayerDomainRepository repository) {
        byte[] payload;
        try {
            payload = buildDatabasePayload(records, reservations, meta);
        } catch (IOException failure) {
            logFailure("save", playerId, failure);
            return CompletableFuture.completedFuture(false);
        }
        long expectedRevision = databaseRevisions.getOrDefault(playerId, 0L);
        return repository
                .save(playerId, DATABASE_DOMAIN, snapshot.playerName(), payload, DATABASE_SCHEMA_VERSION,
                        expectedRevision)
                .handle((result, throwable) -> {
                    if (throwable == null && result != null
                            && result.outcome() == DomainSaveResult.Outcome.COMMITTED) {
                        databaseRevisions.put(playerId, result.revision());
                        return true;
                    }
                    Throwable failure = throwable == null
                            ? new IllegalStateException("数据库保存未提交: "
                                    + (result == null ? "unknown" : result.outcome()))
                            : AsyncFailures.unwrap(throwable);
                    logFailure("save", playerId, failure);
                    return false;
                });
    }

    private byte[] buildDatabasePayload(List<StorageDataFile.Record> records,
            List<StorageDataFile.ReservationRecord> reservations,
            StorageMetaFile.Meta meta) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.putAll(StorageMetaFile.toValues(meta));
        values.put("data", Base64.getEncoder().encodeToString(dataFile.encode(records, reservations)));
        return YamlFiles.dump(values).getBytes(StandardCharsets.UTF_8);
    }

    public synchronized FlushResult flushAndSeal(long timeout, TimeUnit unit) {
        if (flushResult != null) {
            return flushResult;
        }
        long deadline = System.nanoTime() + Math.max(0L, unit.toNanos(timeout));
        cache.seal();

        List<PlayerStorageCache.SaveTicket> tickets = cache.snapshotDirtyEntries();
        List<CompletableFuture<Boolean>> saves = new ArrayList<>(tickets.size());
        for (PlayerStorageCache.SaveTicket ticket : tickets) {
            saves.add(cache.enqueueSaveAsync(ticket, this::writeTicket));
        }
        CompletableFuture<Void> all = saves.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new));
        AsyncWaits.awaitUntil(all, deadline);

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


    private CompletableFuture<Void> acquireDatabaseLease(UUID playerId) {
        PlayerDataLease lease = databaseLease;
        if (lease == null) {
            return CompletableFuture.completedFuture(null);
        }
        return PlayerDataLeases.acquireWithRetry(lease, playerId, 5);
    }


    private void logFailure(String stage, UUID playerId, Throwable throwable) {
        LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.data_handling_failed", Map.of("player", playerId, "stage", stage, "error", describe(throwable)));
    }

    private static String describe(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : " " + message);
    }

}
