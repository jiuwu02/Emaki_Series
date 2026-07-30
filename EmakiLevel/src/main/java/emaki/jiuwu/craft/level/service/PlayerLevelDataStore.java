package emaki.jiuwu.craft.level.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.DrainResult;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;

public final class PlayerLevelDataStore {

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

    private record PendingLoad(long generation, CompletableFuture<PlayerLevelData> future) {
    }

    private final File dataFolder;
    private final Logger logger;
    private final Supplier<AsyncYamlFiles> asyncYamlFilesSupplier;
    private final PlayerLevelDataCache cache = new PlayerLevelDataCache();
    private final Map<UUID, PendingLoad> pendingLoads = new ConcurrentHashMap<>();
    private FlushResult flushResult;

    public PlayerLevelDataStore(EmakiLevelPlugin plugin) {
        this(plugin, null);
    }

    public PlayerLevelDataStore(EmakiLevelPlugin plugin, Supplier<AsyncYamlFiles> asyncYamlFilesSupplier) {
        this(
                Objects.requireNonNull(plugin, "plugin").getDataFolder(),
                plugin.getLogger(),
                asyncYamlFilesSupplier
        );
    }

    PlayerLevelDataStore(File dataFolder,
            Logger logger,
            Supplier<AsyncYamlFiles> asyncYamlFilesSupplier) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.logger = logger == null ? Logger.getLogger(PlayerLevelDataStore.class.getName()) : logger;
        this.asyncYamlFilesSupplier = asyncYamlFilesSupplier;
    }

    public CompletableFuture<PlayerLevelData> beginSession(Player player, Map<String, LevelTypeConfig> types) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        return beginSession(player.getUniqueId(), player.getName(), types, true);
    }

    public CompletableFuture<PlayerLevelData> getOrLoadAsync(UUID uuid, Map<String, LevelTypeConfig> types) {
        return beginSession(uuid, null, types, true, true);
    }

    public PlayerLevelData getOrLoad(UUID uuid, Map<String, LevelTypeConfig> types) {
        if (uuid == null) {
            return null;
        }
        PlayerLevelData active = cached(uuid);
        if (active == null && !cache.isSealed()) {
            getOrLoadAsync(uuid, types);
        }
        return active;
    }

    public PlayerLevelData cached(UUID uuid) {
        return uuid == null ? null : cache.activeData(uuid);
    }

    public Map<UUID, PlayerLevelData> cachedData() {
        return cache.activeDataSnapshot();
    }

    public long currentGeneration(UUID uuid) {
        return uuid == null ? 0L : cache.generation(uuid);
    }

    public boolean isKnownGeneration(UUID uuid, long generation) {
        return uuid != null && cache.isKnownGeneration(uuid, generation);
    }

    public boolean isCurrentGeneration(UUID uuid, long generation) {
        return uuid != null && cache.isCurrentGeneration(uuid, generation);
    }

    public boolean isSessionWritable(UUID uuid) {
        return uuid != null && cache.currentTicket(uuid) != null;
    }

    public <R> R mutate(UUID uuid,
            Map<String, LevelTypeConfig> types,
            Function<PlayerLevelData, R> mutation) {
        return mutate(uuid, 0L, types, mutation);
    }

    public <R> R mutate(UUID uuid,
            long expectedGeneration,
            Map<String, LevelTypeConfig> types,
            Function<PlayerLevelData, R> mutation) {
        Objects.requireNonNull(mutation, "mutation");
        if (uuid == null || cache.isSealed()) {
            return null;
        }
        if (cache.snapshot(uuid) == null) {
            getOrLoadAsync(uuid, types);
        }
        return cache.mutate(uuid, expectedGeneration, data -> {
            ensureTypes(data, types);
            return mutation.apply(data);
        });
    }

    public void ensureTypesForCached(Map<String, LevelTypeConfig> types) {
        for (UUID playerId : cache.activeDataSnapshot().keySet()) {
            cache.mutate(playerId, 0L, data -> {
                ensureTypes(data, types);
                return null;
            });
        }
    }

    public CompletableFuture<Boolean> saveAsync(UUID uuid) {
        if (uuid == null || cache.isSealed()) {
            return CompletableFuture.completedFuture(false);
        }
        return saveTicket(cache.snapshotForSave(uuid, 0L, false));
    }

    public CompletableFuture<Integer> saveAllAsync() {
        if (cache.isSealed()) {
            return CompletableFuture.completedFuture(0);
        }
        List<PlayerLevelDataCache.SaveTicket> tickets = cache.snapshotDirtyEntries();
        List<CompletableFuture<Boolean>> futures = saveTickets(tickets);
        if (futures.isEmpty()) {
            return CompletableFuture.completedFuture(0);
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

    public CompletableFuture<Boolean> unloadAsync(UUID uuid) {
        return unloadAsync(uuid, 0L);
    }

    public CompletableFuture<Boolean> unloadAsync(UUID uuid, long expectedGeneration) {
        if (uuid == null || cache.isSealed()) {
            return CompletableFuture.completedFuture(false);
        }
        return saveTicket(cache.snapshotForSave(uuid, expectedGeneration, true));
    }

    public CompletableFuture<Void> waitForPendingSaves() {
        CompletableFuture<Void> logicalIdle = cache.waitForIdle();
        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        return asyncYamlFiles == null
                ? logicalIdle
                : CompletableFuture.allOf(logicalIdle, asyncYamlFiles.waitForIdle());
    }

    public synchronized FlushResult flushAndSeal(long timeout, TimeUnit unit) {
        if (flushResult != null) {
            return flushResult;
        }
        Objects.requireNonNull(unit, "unit");
        long deadline = System.nanoTime() + Math.max(0L, unit.toNanos(timeout));
        cache.seal();

        List<PlayerLevelDataCache.SaveTicket> tickets = cache.snapshotDirtyEntries();
        List<CompletableFuture<Boolean>> saveFutures = saveTickets(tickets);
        CompletableFuture<Void> savesComplete = saveFutures.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(saveFutures.toArray(CompletableFuture[]::new));
        awaitUntil(savesComplete, deadline);

        int savedEntries = 0;
        for (CompletableFuture<Boolean> saveFuture : saveFutures) {
            if (saveFuture.isDone()
                    && !saveFuture.isCompletedExceptionally()
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

    public void load(Player player, Map<String, LevelTypeConfig> types) {
        beginSession(player, types);
    }

    public void save(UUID uuid) {
        saveAsync(uuid);
    }

    public void saveAll() {
        saveAllAsync();
    }

    public void unload(UUID uuid) {
        unloadAsync(uuid);
    }

    public List<PlayerLevelData> allKnownData(Map<String, LevelTypeConfig> types) {
        return List.copyOf(cache.activeDataSnapshot().values());
    }

    public CompletableFuture<List<PlayerLevelData>> allKnownDataAsync(Map<String, LevelTypeConfig> types) {
        Map<UUID, PlayerLevelData> known = new LinkedHashMap<>(cache.activeDataSnapshot());
        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        if (asyncYamlFiles == null) {
            return loadKnownFilesAsync(
                    known,
                    dataDirectory().listFiles((directory, name) -> name.endsWith(".yml")),
                    types
            );
        }
        return asyncYamlFiles.read(
                        "level-player-data-list",
                        () -> dataDirectory().listFiles((directory, name) -> name.endsWith(".yml"))
                )
                .thenCompose(files -> loadKnownFilesAsync(known, files, types));
    }

    private CompletableFuture<List<PlayerLevelData>> loadKnownFilesAsync(Map<UUID, PlayerLevelData> known,
            File[] files,
            Map<String, LevelTypeConfig> types) {
        if (files == null || files.length == 0) {
            return CompletableFuture.completedFuture(List.copyOf(known.values()));
        }

        List<CompletableFuture<PlayerLevelData>> loads = new ArrayList<>();
        for (File file : files) {
            UUID playerId = parsePlayerId(file.getName());
            if (playerId == null || known.containsKey(playerId)) {
                continue;
            }
            PendingLoad pending = pendingLoads.get(playerId);
            if (pending != null && cache.isKnownGeneration(playerId, pending.generation())) {
                loads.add(pending.future());
                continue;
            }
            loads.add(loadDetached(playerId, file, types));
        }
        if (loads.isEmpty()) {
            return CompletableFuture.completedFuture(List.copyOf(known.values()));
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    for (CompletableFuture<PlayerLevelData> load : loads) {
                        PlayerLevelData data = load.getNow(null);
                        if (data != null) {
                            known.put(data.uuid(), data.copy());
                        }
                    }
                    return List.copyOf(known.values());
                });
    }

    CompletableFuture<PlayerLevelData> beginSession(UUID playerId,
            String playerName,
            Map<String, LevelTypeConfig> types,
            boolean retryFailed) {
        return beginSession(playerId, playerName, types, retryFailed, false);
    }

    private CompletableFuture<PlayerLevelData> beginSession(UUID playerId,
            String playerName,
            Map<String, LevelTypeConfig> types,
            boolean retryFailed,
            boolean propagateLoadFailure) {
        if (cache.isSealed()) {
            return CompletableFuture.completedFuture(null);
        }

        PlayerLevelDataCache.Snapshot current = cache.snapshot(playerId);
        if (current != null) {
            if (current.lifecycle() == PlayerLevelDataCache.Lifecycle.ACTIVE && current.loadWritable()) {
                cache.mutate(playerId, current.generation(), data -> {
                    if (playerName != null) {
                        data.name(playerName);
                    }
                    ensureTypes(data, types);
                    return null;
                });
                return CompletableFuture.completedFuture(cache.activeData(playerId));
            }
            PendingLoad pending = pendingLoads.get(playerId);
            if (current.lifecycle() == PlayerLevelDataCache.Lifecycle.LOADING
                    && pending != null
                    && pending.generation() == current.generation()) {
                return pending.future();
            }
            if (current.lifecycle() == PlayerLevelDataCache.Lifecycle.LOAD_FAILED && !retryFailed) {
                return CompletableFuture.completedFuture(null);
            }
        }

        File file = playerFile(playerId);
        PlayerLevelData initial = createDefault(playerId, types);
        PlayerLevelDataCache.SessionTicket ticket = cache.beginSession(playerId, initial, false);
        if (ticket == null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<PlayerLevelData> loadResult = new CompletableFuture<>();
        PendingLoad pending = new PendingLoad(ticket.generation(), loadResult);
        pendingLoads.put(playerId, pending);
        loadResult.whenComplete((ignored, throwable) -> pendingLoads.remove(playerId, pending));

        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        CompletableFuture<PlayerLevelData> physicalLoad;
        if (asyncYamlFiles == null) {
            physicalLoad = CompletableFuture.failedFuture(
                    new IllegalStateException("AsyncYamlFiles is unavailable"));
        } else {
            physicalLoad = cache.waitForIdle(playerId)
                    .thenCompose(ignored -> asyncYamlFiles.load(file))
                    .thenApply(section -> installLoaded(ticket, readData(playerId, section, types), playerName));
        }
        physicalLoad = physicalLoad.exceptionally(throwable -> {
            Throwable failure = AsyncFailures.unwrapOnce(throwable);
            logLoadFailure(playerId, failure);
            PlayerLevelData fallback = createDefault(playerId, types);
            cache.installLoadFailure(ticket, fallback);
            if (propagateLoadFailure) {
                throw new CompletionException(failure);
            }
            return null;
        });
        physicalLoad.whenComplete((data, throwable) -> {
            if (throwable == null) {
                loadResult.complete(data);
            } else {
                loadResult.completeExceptionally(throwable);
            }
        });
        return loadResult;
    }

    private PlayerLevelData installLoaded(PlayerLevelDataCache.SessionTicket ticket,
            PlayerLevelData loaded,
            String playerName) {
        if (cache.installLoaded(ticket, loaded) == PlayerLevelDataCache.CommitResult.COMMITTED) {
            if (playerName != null) {
                cache.mutate(ticket.playerId(), ticket.generation(), data -> {
                    data.name(playerName);
                    return null;
                });
            }
            return cache.activeData(ticket.playerId());
        }
        return cache.activeData(ticket.playerId());
    }

    private CompletableFuture<PlayerLevelData> loadDetached(UUID playerId,
            File file,
            Map<String, LevelTypeConfig> types) {
        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        if (asyncYamlFiles == null) {
            try {
                return CompletableFuture.completedFuture(readData(playerId, YamlFiles.load(file), types));
            } catch (RuntimeException exception) {
                logLoadFailure(playerId, exception);
                return CompletableFuture.completedFuture(null);
            }
        }
        return cache.waitForIdle(playerId)
                .thenCompose(ignored -> asyncYamlFiles.load(file))
                .thenApply(section -> readData(playerId, section, types))
                .exceptionally(throwable -> {
                    logLoadFailure(playerId, AsyncFailures.unwrapOnce(throwable));
                    return null;
                });
    }

    private List<CompletableFuture<Boolean>> saveTickets(List<PlayerLevelDataCache.SaveTicket> tickets) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>(tickets.size());
        for (PlayerLevelDataCache.SaveTicket ticket : tickets) {
            futures.add(saveTicket(ticket));
        }
        return futures;
    }

    private CompletableFuture<Boolean> saveTicket(PlayerLevelDataCache.SaveTicket ticket) {
        if (ticket == null) {
            return CompletableFuture.completedFuture(true);
        }
        return cache.enqueueSave(ticket, current -> writeSnapshot(current.playerId(), current.snapshot()));
    }

    private CompletableFuture<Boolean> writeSnapshot(UUID playerId, PlayerLevelData snapshot) {
        File file = playerFile(playerId);
        Map<String, Object> serialized = serialize(snapshot);
        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        if (asyncYamlFiles == null) {
            try {
                YamlFiles.save(file, serialized);
                return CompletableFuture.completedFuture(true);
            } catch (IOException exception) {
                logSaveFailure(playerId, exception);
                return CompletableFuture.completedFuture(false);
            }
        }
        return asyncYamlFiles.save(file, serialized)
                .thenApply(ignored -> true)
                .exceptionally(throwable -> {
                    logSaveFailure(playerId, AsyncFailures.unwrapOnce(throwable));
                    return false;
                });
    }

    private PlayerLevelData createDefault(UUID playerId, Map<String, LevelTypeConfig> types) {
        PlayerLevelData data = new PlayerLevelData(playerId, playerId.toString());
        ensureTypes(data, types);
        data.clearDirty();
        return data;
    }

    private PlayerLevelData readData(UUID playerId,
            YamlSection section,
            Map<String, LevelTypeConfig> types) {
        String fallbackName = playerId.toString();
        PlayerLevelData data = new PlayerLevelData(playerId, section.getString("name", fallbackName));
        YamlSection levelsSection = section.getSection("levels");
        if (levelsSection != null) {
            for (String typeId : levelsSection.getKeys(false)) {
                String normalizedTypeId = Texts.normalizeId(typeId);
                YamlSection levelSection = levelsSection.getSection(typeId);
                if (Texts.isBlank(normalizedTypeId) || levelSection == null) {
                    continue;
                }
                data.put(normalizedTypeId, new PlayerLevelEntry(
                        levelSection.getInt("level", defaultLevel(types.get(normalizedTypeId))),
                        levelSection.getDouble("exp", 0D),
                        levelSection.getDouble("total_exp", 0D),
                        toLong(levelSection.get("updated_at"), System.currentTimeMillis())
                ));
            }
        }
        ensureTypes(data, types);
        data.clearDirty();
        return data;
    }

    private void ensureTypes(PlayerLevelData data, Map<String, LevelTypeConfig> types) {
        if (data == null || types == null) {
            return;
        }
        for (LevelTypeConfig type : types.values()) {
            data.levels().computeIfAbsent(type.id(), ignored -> {
                data.markDirty();
                return new PlayerLevelEntry(type.startLevel(), 0D, 0D, System.currentTimeMillis());
            });
        }
    }

    private Map<String, Object> serialize(PlayerLevelData data) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", 1);
        root.put("uuid", data.uuid().toString());
        root.put("name", data.name());
        Map<String, Object> levels = new LinkedHashMap<>();
        for (Map.Entry<String, PlayerLevelEntry> entry : data.levels().entrySet()) {
            PlayerLevelEntry level = entry.getValue();
            if (level == null) {
                continue;
            }
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("level", level.level());
            values.put("exp", level.exp());
            values.put("total_exp", level.totalExp());
            values.put("updated_at", level.updatedAt());
            levels.put(entry.getKey(), values);
        }
        root.put("levels", levels);
        return root;
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

    private File dataDirectory() {
        return new File(dataFolder, "data");
    }

    private File playerFile(UUID playerId) {
        return new File(dataDirectory(), playerId + ".yml");
    }

    private UUID parsePlayerId(String fileName) {
        try {
            return UUID.fromString(fileName.substring(0, fileName.length() - 4));
        } catch (IllegalArgumentException | IndexOutOfBoundsException exception) {
            return null;
        }
    }

    private AsyncYamlFiles asyncYamlFiles() {
        return asyncYamlFilesSupplier == null ? null : asyncYamlFilesSupplier.get();
    }

    private void logLoadFailure(UUID playerId, Throwable throwable) {
        logger.log(Level.WARNING,
                "[LevelDataStore] Failed to load " + playerId
                        + "; this session remains read-only to protect the existing file",
                throwable);
    }

    private void logSaveFailure(UUID playerId, Throwable throwable) {
        logger.log(Level.WARNING,
                "[LevelDataStore] Failed to save " + playerId,
                throwable);
    }

    private static long toLong(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private int defaultLevel(LevelTypeConfig type) {
        return type == null ? 1 : type.startLevel();
    }
}
