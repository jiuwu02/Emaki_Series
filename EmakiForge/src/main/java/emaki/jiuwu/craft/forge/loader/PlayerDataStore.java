package emaki.jiuwu.craft.forge.loader;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.async.AsyncFileService.DrainResult;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.PlayerData;

public final class PlayerDataStore {

    private static final String META_KEY = "_meta";

    private record PersistentVersion(long epoch, long revision, boolean tombstone) {

        private boolean isNewerThan(PersistentVersion other) {
            if (other == null) {
                return true;
            }
            return epoch > other.epoch || epoch == other.epoch && revision > other.revision;
        }
    }

    private record LoadedPlayerData(PlayerData data, PersistentVersion version) {
    }

    public record FlushResult(int savedEntries,
            int failedEntries,
            int remainingDirtyEntries,
            DrainResult drainResult) {

        public boolean clean() {
            return failedEntries == 0
                    && remainingDirtyEntries == 0
                    && drainResult != null
                    && drainResult.drained()
                    && drainResult.failures().isEmpty();
        }
    }

    public enum GuaranteeCounterUpdate {
        NONE,
        INCREMENT,
        RESET
    }

    private final EmakiForgePlugin plugin;
    private final Supplier<AsyncYamlFiles> asyncYamlFilesSupplier;
    private final PlayerDataCache cache = new PlayerDataCache();
    private final Map<String, PersistentVersion> persistentVersions = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> sessionEpochs = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Boolean>> closingSessions = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> persistenceTails = new HashMap<>();
    private final Object persistenceLock = new Object();
    private FlushResult flushResult;

    public PlayerDataStore(EmakiForgePlugin plugin, Supplier<AsyncYamlFiles> asyncYamlFilesSupplier) {
        this.plugin = plugin;
        this.asyncYamlFilesSupplier = asyncYamlFilesSupplier;
    }

    public void load() {
        File directory = plugin.dataPath("data").toFile();
        try {
            YamlFiles.ensureDirectory(directory.toPath());
        } catch (IOException exception) {
            plugin.messageService().warning("console.player_data_directory_create_failed", Map.of(
                    "path", directory.getPath()
            ));
            return;
        }
        recoverPersistentVersions(directory);
    }

    public CompletableFuture<PlayerData> beginSession(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        String key = uuid.toString();
        CompletableFuture<Boolean> closing = closingSessions.get(key);
        if (closing == null) {
            return beginSessionNow(key);
        }
        return closing.handle((saved, throwable) -> throwable == null && Boolean.TRUE.equals(saved))
                .thenCompose(closed -> closed
                        ? beginSessionNow(key)
                        : CompletableFuture.completedFuture(currentSnapshot(key)));
    }

    private CompletableFuture<PlayerData> beginSessionNow(String key) {
        File file = playerFile(key);
        boolean existingFile = file.exists();
        PlayerDataCache.LoadTicket ticket = cache.beginSession(key, existingFile);
        if (ticket == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (!existingFile) {
            PlayerData created = new PlayerData(key);
            PersistentVersion recovered = persistentVersions.get(key);
            long epoch = reserveEpoch(key, recovered);
            cache.installLoaded(ticket, created, epoch, 0L);
            return CompletableFuture.completedFuture(created.copy());
        }

        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        if (asyncYamlFiles == null) {
            return CompletableFuture.completedFuture(loadSynchronously(ticket, file));
        }
        return asyncYamlFiles.load(file)
                .thenApply(section -> {
                    LoadedPlayerData recovered = decodePlayerData(key, section);
                    persistentVersions.merge(key, recovered.version(), this::newerVersion);
                    return cache.installLoaded(
                            ticket,
                            recovered.data(),
                            reserveEpoch(key, recovered.version()),
                            0L
                    ) == PlayerDataCache.CommitResult.COMMITTED
                            ? recovered.data().copy()
                            : null;
                })
                .exceptionally(throwable -> {
                    logLoadFailure(key, unwrap(throwable));
                    PlayerData fallback = new PlayerData(key);
                    return cache.installLoadFailure(ticket, fallback) == PlayerDataCache.CommitResult.COMMITTED
                            ? fallback.copy()
                            : null;
                });
    }

    public long currentGeneration(UUID uuid) {
        return uuid == null ? 0L : cache.generation(uuid.toString());
    }

    public long ensureCurrentGeneration(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        ensureSessionSynchronously(uuid);
        return cache.generation(uuid.toString());
    }

    public boolean isCurrentGeneration(UUID uuid, long generation) {
        return uuid != null && cache.isCurrentGeneration(uuid.toString(), generation);
    }

    public boolean isSessionWritable(UUID uuid) {
        return uuid != null && cache.isWritable(uuid.toString());
    }

    public PlayerData get(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        ensureSessionSynchronously(uuid);
        return currentSnapshot(uuid.toString());
    }

    public CompletableFuture<Boolean> saveAsync(UUID uuid) {
        if (uuid == null || cache.isSealed()) {
            return CompletableFuture.completedFuture(false);
        }
        PlayerDataCache.SaveTicket ticket = cache.snapshotForSave(uuid.toString(), false);
        return saveTicket(ticket);
    }

    public CompletableFuture<Boolean> saveAndClearAsync(UUID uuid) {
        return saveAndClearAsync(uuid, 0L);
    }

    public CompletableFuture<Boolean> saveAndClearAsync(UUID uuid, long expectedGeneration) {
        if (uuid == null || cache.isSealed()) {
            return CompletableFuture.completedFuture(false);
        }
        String key = uuid.toString();
        PlayerDataCache.SaveTicket ticket = cache.snapshotForSave(key, expectedGeneration, true);
        if (ticket == null) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Boolean> closing = saveTicket(ticket);
        closingSessions.put(key, closing);
        closing.whenComplete((ignored, throwable) -> closingSessions.remove(key, closing));
        return closing;
    }

    public CompletableFuture<Integer> saveAllAsync() {
        if (cache.isSealed()) {
            return CompletableFuture.completedFuture(0);
        }
        List<PlayerDataCache.SaveTicket> tickets = cache.snapshotDirtyEntries();
        if (tickets.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        List<CompletableFuture<Boolean>> futures = new ArrayList<>(tickets.size());
        for (PlayerDataCache.SaveTicket ticket : tickets) {
            futures.add(saveTicket(ticket));
        }
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

    public CompletableFuture<Void> waitForPendingSaves() {
        CompletableFuture<Void> logicalIdle = cache.waitForIdle();
        CompletableFuture<Void> persistentIdle = persistenceIdle();
        return CompletableFuture.allOf(logicalIdle, persistentIdle).thenCompose(ignored -> {
            AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
            return asyncYamlFiles == null
                    ? CompletableFuture.completedFuture(null)
                    : asyncYamlFiles.waitForIdle();
        });
    }

    public synchronized FlushResult flushAndSeal(long timeout, TimeUnit unit) {
        if (flushResult != null) {
            return flushResult;
        }
        Objects.requireNonNull(unit, "unit");
        long deadline = System.nanoTime() + Math.max(0L, unit.toNanos(timeout));
        cache.seal();

        List<PlayerDataCache.SaveTicket> tickets = cache.snapshotDirtyEntries();
        List<CompletableFuture<Boolean>> saveFutures = new ArrayList<>(tickets.size());
        for (PlayerDataCache.SaveTicket ticket : tickets) {
            saveFutures.add(saveTicket(ticket));
        }
        CompletableFuture<Void> savesComplete = saveFutures.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(saveFutures.toArray(CompletableFuture[]::new));
        CompletableFuture<Void> persistenceComplete = CompletableFuture.allOf(savesComplete, persistenceIdle());
        awaitUntil(persistenceComplete, deadline);

        int savedEntries = 0;
        for (CompletableFuture<Boolean> saveFuture : saveFutures) {
            if (saveFuture.isDone() && !saveFuture.isCompletedExceptionally()
                    && Boolean.TRUE.equals(saveFuture.getNow(false))) {
                savedEntries++;
            }
        }
        int failedEntries = tickets.size() - savedEntries;

        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        DrainResult drainResult;
        if (asyncYamlFiles == null) {
            int pending = (int) saveFutures.stream().filter(future -> !future.isDone()).count();
            drainResult = new DrainResult(pending == 0, pending, List.of());
        } else {
            long remainingNanos = Math.max(0L, deadline - System.nanoTime());
            drainResult = asyncYamlFiles.sealAndDrain(remainingNanos, TimeUnit.NANOSECONDS);
        }
        flushResult = new FlushResult(savedEntries, failedEntries, cache.dirtyCount(), drainResult);
        return flushResult;
    }

    public void clear(UUID uuid) {
        if (uuid != null) {
            cache.removeCurrent(uuid.toString());
        }
    }

    public CompletableFuture<Boolean> deleteAsync(UUID uuid) {
        if (uuid == null || cache.isSealed()) {
            return CompletableFuture.completedFuture(false);
        }
        String key = uuid.toString();
        cache.removeCurrent(key);
        return enqueuePersistence(key, () -> loadPersistentVersion(key).thenCompose(current -> {
            PersistentVersion tombstone = new PersistentVersion(reserveEpoch(key, current), 0L, true);
            return persistSnapshot(key, tombstone, Map.of("uuid", key));
        }));
    }

    public void recordCraft(UUID uuid, String recipeId) {
        mutate(uuid, 0L, data -> data.recordCraft(recipeId, Instant.now().toString()));
    }

    public boolean recordCraftIfCurrent(UUID uuid, long generation, String recipeId) {
        return mutate(uuid, generation, data -> data.recordCraft(recipeId, Instant.now().toString()));
    }

    public boolean recordSuccessfulForgeIfCurrent(UUID uuid,
            long generation,
            String recipeId,
            GuaranteeCounterUpdate guaranteeUpdate) {
        String timestamp = Instant.now().toString();
        GuaranteeCounterUpdate update = guaranteeUpdate == null ? GuaranteeCounterUpdate.NONE : guaranteeUpdate;
        return mutate(uuid, generation, data -> {
            if (update == GuaranteeCounterUpdate.INCREMENT) {
                data.incrementGuaranteeCounter(recipeId);
            } else if (update == GuaranteeCounterUpdate.RESET) {
                data.resetGuaranteeCounter(recipeId);
            }
            data.recordCraft(recipeId, timestamp);
        });
    }

    public boolean hasCrafted(UUID uuid, String recipeId) {
        if (uuid == null) {
            return false;
        }
        ensureSessionSynchronously(uuid);
        Boolean crafted = cache.read(uuid.toString(), data -> data.hasCrafted(recipeId));
        return Boolean.TRUE.equals(crafted);
    }

    public int craftCount(UUID uuid, String recipeId) {
        if (uuid == null) {
            return 0;
        }
        ensureSessionSynchronously(uuid);
        Integer count = cache.read(uuid.toString(), data -> data.craftCount(recipeId));
        return count == null ? 0 : count;
    }

    public int guaranteeCounter(UUID uuid, String key) {
        if (uuid == null) {
            return 0;
        }
        ensureSessionSynchronously(uuid);
        Integer counter = cache.read(uuid.toString(), data -> data.guaranteeCounter(key));
        return counter == null ? 0 : counter;
    }

    public int guaranteeCounterIfCurrent(UUID uuid, long generation, String key) {
        if (!isCurrentGeneration(uuid, generation)) {
            return 0;
        }
        Integer counter = cache.read(uuid.toString(), data -> data.guaranteeCounter(key));
        return counter == null ? 0 : counter;
    }

    public void incrementGuaranteeCounter(UUID uuid, String key) {
        mutate(uuid, 0L, data -> data.incrementGuaranteeCounter(key));
    }

    public boolean incrementGuaranteeCounterIfCurrent(UUID uuid, long generation, String key) {
        return mutate(uuid, generation, data -> data.incrementGuaranteeCounter(key));
    }

    public void resetGuaranteeCounter(UUID uuid, String key) {
        mutate(uuid, 0L, data -> data.resetGuaranteeCounter(key));
    }

    public boolean resetGuaranteeCounterIfCurrent(UUID uuid, long generation, String key) {
        return mutate(uuid, generation, data -> data.resetGuaranteeCounter(key));
    }

    private boolean mutate(UUID uuid, long expectedGeneration, java.util.function.Consumer<PlayerData> mutation) {
        if (uuid == null) {
            return false;
        }
        if (expectedGeneration <= 0L) {
            ensureSessionSynchronously(uuid);
        }
        return cache.update(uuid.toString(), expectedGeneration, mutation);
    }

    private CompletableFuture<Boolean> saveTicket(PlayerDataCache.SaveTicket ticket) {
        if (ticket == null) {
            return CompletableFuture.completedFuture(false);
        }
        return cache.enqueueSave(ticket, this::writeSnapshot);
    }

    private CompletableFuture<Boolean> writeSnapshot(PlayerDataCache.SaveTicket ticket) {
        PersistentVersion incoming = new PersistentVersion(
                ticket.epoch(),
                ticket.persistentRevision(),
                ticket.tombstone()
        );
        Map<String, Object> snapshot = ticket.tombstone()
                ? Map.of("uuid", ticket.uuid())
                : ticket.snapshot().toMap();
        return enqueuePersistence(ticket.uuid(), () -> loadPersistentVersion(ticket.uuid()).thenCompose(current -> {
            if (!incoming.isNewerThan(current)) {
                return CompletableFuture.completedFuture(incoming.equals(current));
            }
            return persistSnapshot(ticket.uuid(), incoming, snapshot);
        }));
    }

    private CompletableFuture<Void> persistenceIdle() {
        synchronized (persistenceLock) {
            return persistenceTails.isEmpty()
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.allOf(persistenceTails.values().toArray(CompletableFuture[]::new));
        }
    }

    private CompletableFuture<Boolean> enqueuePersistence(String uuid,
            Supplier<CompletableFuture<Boolean>> operation) {
        CompletableFuture<Boolean> result;
        CompletableFuture<Void> tail;
        synchronized (persistenceLock) {
            CompletableFuture<Void> previous = persistenceTails.getOrDefault(
                    uuid,
                    CompletableFuture.completedFuture(null)
            );
            result = previous.handle((ignored, throwable) -> null).thenCompose(ignored -> {
                try {
                    CompletableFuture<Boolean> submitted = operation.get();
                    return submitted == null ? CompletableFuture.completedFuture(false) : submitted;
                } catch (RuntimeException exception) {
                    return CompletableFuture.failedFuture(exception);
                }
            });
            tail = result.handle((ignored, throwable) -> null);
            persistenceTails.put(uuid, tail);
        }
        CompletableFuture<Void> completedTail = tail;
        tail.whenComplete((ignored, throwable) -> {
            synchronized (persistenceLock) {
                persistenceTails.remove(uuid, completedTail);
            }
        });
        return result;
    }

    private CompletableFuture<PersistentVersion> loadPersistentVersion(String uuid) {
        File file = playerFile(uuid);
        PersistentVersion recovered = persistentVersions.get(uuid);
        if (!file.exists()) {
            return CompletableFuture.completedFuture(recovered == null
                    ? new PersistentVersion(0L, 0L, false)
                    : recovered);
        }
        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        if (asyncYamlFiles == null) {
            try {
                PersistentVersion current = persistentVersion(YamlFiles.load(file));
                return CompletableFuture.completedFuture(newerVersion(recovered, current));
            } catch (RuntimeException exception) {
                logSaveFailure(uuid, exception);
                return CompletableFuture.failedFuture(exception);
            }
        }
        return asyncYamlFiles.load(file).handle((section, throwable) -> {
            if (throwable != null) {
                Throwable cause = unwrap(throwable);
                logSaveFailure(uuid, cause);
                throw new CompletionException(cause);
            }
            return newerVersion(recovered, persistentVersion(section));
        });
    }

    private CompletableFuture<Boolean> persistSnapshot(String uuid,
            PersistentVersion version,
            Map<String, Object> dataSnapshot) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        if (dataSnapshot != null) {
            serialized.putAll(dataSnapshot);
        }
        serialized.put(META_KEY, Map.of(
                "epoch", version.epoch(),
                "revision", version.revision(),
                "tombstone", version.tombstone()
        ));
        File file = playerFile(uuid);
        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        if (asyncYamlFiles == null) {
            try {
                YamlFiles.save(file, serialized);
                persistentVersions.merge(uuid, version, this::newerVersion);
                return CompletableFuture.completedFuture(true);
            } catch (IOException exception) {
                logSaveFailure(uuid, exception);
                return CompletableFuture.completedFuture(false);
            }
        }
        return asyncYamlFiles.save(file, serialized)
                .thenApply(ignored -> {
                    persistentVersions.merge(uuid, version, this::newerVersion);
                    return true;
                })
                .exceptionally(throwable -> {
                    logSaveFailure(uuid, unwrap(throwable));
                    return false;
                });
    }

    private LoadedPlayerData decodePlayerData(String uuid, YamlSection section) {
        PersistentVersion version = persistentVersion(section);
        PlayerData data = version.tombstone()
                ? new PlayerData(uuid)
                : PlayerData.fromConfig(uuid, section);
        return new LoadedPlayerData(data, version);
    }

    private PersistentVersion persistentVersion(YamlSection section) {
        Object metadata = section == null ? null : section.get(META_KEY);
        long epoch = Math.max(0L, Numbers.tryParseLong(ConfigNodes.get(metadata, "epoch"), 0L));
        long revision = Math.max(0L, Numbers.tryParseLong(ConfigNodes.get(metadata, "revision"), 0L));
        boolean tombstone = ConfigNodes.bool(metadata, "tombstone", false);
        return new PersistentVersion(epoch, revision, tombstone);
    }

    private PersistentVersion newerVersion(PersistentVersion first, PersistentVersion second) {
        if (first == null) {
            return second == null ? new PersistentVersion(0L, 0L, false) : second;
        }
        if (second == null) {
            return first;
        }
        if (second.isNewerThan(first)) {
            return second;
        }
        if (first.isNewerThan(second)) {
            return first;
        }
        return first.tombstone() ? first : second;
    }

    private long reserveEpoch(String uuid, PersistentVersion version) {
        long observed = version == null ? 0L : Math.max(0L, version.epoch());
        AtomicLong counter = sessionEpochs.computeIfAbsent(uuid, ignored -> new AtomicLong(observed));
        while (true) {
            long current = counter.get();
            long base = Math.max(current, observed);
            long next = base == Long.MAX_VALUE ? Long.MAX_VALUE : base + 1L;
            if (counter.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    private void recoverPersistentVersions(File directory) {
        File[] files = directory == null ? null : directory.listFiles(file -> file.isFile()
                && file.getName().endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String name = file.getName();
            String uuid = name.substring(0, name.length() - 4);
            try {
                UUID.fromString(uuid);
                PersistentVersion recovered = persistentVersion(YamlFiles.load(file));
                persistentVersions.put(uuid, recovered);
                sessionEpochs.computeIfAbsent(uuid, ignored -> new AtomicLong())
                        .accumulateAndGet(recovered.epoch(), Math::max);
            } catch (IllegalArgumentException ignored) {
                // Ignore non-player YAML files in the data directory.
            } catch (RuntimeException exception) {
                logLoadFailure(uuid, exception);
            }
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

    private void ensureSessionSynchronously(UUID uuid) {
        String key = uuid.toString();
        if (cache.contains(key) || cache.isSealed()) {
            return;
        }
        File file = playerFile(key);
        PlayerDataCache.LoadTicket ticket = cache.beginSession(key, file.exists());
        if (ticket == null) {
            return;
        }
        if (!file.exists()) {
            cache.installLoaded(ticket, new PlayerData(key), reserveEpoch(key, persistentVersions.get(key)), 0L);
            return;
        }
        loadSynchronously(ticket, file);
    }

    private PlayerData loadSynchronously(PlayerDataCache.LoadTicket ticket, File file) {
        try {
            LoadedPlayerData recovered = decodePlayerData(ticket.uuid(), YamlFiles.load(file));
            persistentVersions.merge(ticket.uuid(), recovered.version(), this::newerVersion);
            if (cache.installLoaded(
                    ticket,
                    recovered.data(),
                    reserveEpoch(ticket.uuid(), recovered.version()),
                    0L
            ) == PlayerDataCache.CommitResult.COMMITTED) {
                return recovered.data().copy();
            }
            return currentSnapshot(ticket.uuid());
        } catch (RuntimeException exception) {
            logLoadFailure(ticket.uuid(), exception);
            PlayerData fallback = new PlayerData(ticket.uuid());
            cache.installLoadFailure(ticket, fallback);
            return fallback.copy();
        }
    }

    private PlayerData currentSnapshot(String uuid) {
        PlayerDataCache.VersionedSnapshot snapshot = cache.snapshot(uuid);
        return snapshot == null ? null : snapshot.snapshot();
    }

    private File playerFile(String uuid) {
        return plugin.dataPath("data", uuid + ".yml").toFile();
    }

    private AsyncYamlFiles asyncYamlFiles() {
        return asyncYamlFilesSupplier == null ? null : asyncYamlFilesSupplier.get();
    }

    private void logLoadFailure(String uuid, Throwable throwable) {
        plugin.getLogger().log(java.util.logging.Level.WARNING,
                "[PlayerDataStore] Failed to load player data for " + uuid
                        + "; this session will remain read-only to protect the existing file",
                throwable);
    }

    private void logSaveFailure(String uuid, Throwable throwable) {
        plugin.messageService().warning("console.player_data_save_failed", Map.of(
                "uuid", uuid,
                "error", String.valueOf(throwable == null ? "unknown" : throwable.getMessage())
        ));
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }
}
