package emaki.jiuwu.craft.level.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.api.data.DomainPayloads;
import emaki.jiuwu.craft.corelib.api.data.PlayerDataLeases;
import emaki.jiuwu.craft.corelib.api.async.AsyncWaits;
import emaki.jiuwu.craft.corelib.api.data.DomainSaveResult;
import emaki.jiuwu.craft.corelib.api.data.DomainSnapshot;
import emaki.jiuwu.craft.corelib.api.data.PlayerDataLease;
import emaki.jiuwu.craft.corelib.api.data.PlayerDomainRepository;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.DrainResult;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.log.EmakiLog;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;

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

    private static final String DEBUG_SESSION_MODULE = "common";
    private static final String DATABASE_DOMAIN = "level";
    private static final int DATABASE_SCHEMA_VERSION = 1;

    private final File dataFolder;
    private final Logger logger;
    private final Supplier<AsyncYamlFiles> asyncYamlFilesSupplier;
    private final PlayerLevelDataCache cache;
    private final Map<UUID, PendingLoad> pendingLoads = new ConcurrentHashMap<>();
    private final Map<UUID, Long> databaseRevisions = new ConcurrentHashMap<>();
    private volatile PlayerDomainRepository databaseRepository;
    private volatile PlayerDataLease databaseLease;
    private FlushResult flushResult;

    public PlayerLevelDataStore(EmakiLevelPlugin plugin) {
        this(plugin, null);
    }

    public PlayerLevelDataStore(EmakiLevelPlugin plugin, Supplier<AsyncYamlFiles> asyncYamlFilesSupplier) {
        this(
                Objects.requireNonNull(plugin, "plugin").getDataFolder(),
                plugin.getLogger(),
                asyncYamlFilesSupplier,

                () -> plugin.debugLogger() != null
                        && plugin.debugLogger().shouldLog(DEBUG_SESSION_MODULE, (UUID) null),
                fields -> {
                    if (plugin.debugLogger() != null) {
                        plugin.debugLogger().log(DEBUG_SESSION_MODULE, (UUID) null, "session_line",
                                Map.of("fields", fields == null ? "" : fields));
                    }
                }
        );
    }

    PlayerLevelDataStore(File dataFolder,
            Logger logger,
            Supplier<AsyncYamlFiles> asyncYamlFilesSupplier) {
        this(dataFolder, logger, asyncYamlFilesSupplier, null, null);
    }

    PlayerLevelDataStore(File dataFolder,
            Logger logger,
            Supplier<AsyncYamlFiles> asyncYamlFilesSupplier,
            BooleanSupplier anchorGate,
            Consumer<String> anchorSink) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.logger = logger == null ? EmakiLog.of("EmakiLevel") : logger;
        this.asyncYamlFilesSupplier = asyncYamlFilesSupplier;
        this.cache = new PlayerLevelDataCache(anchorGate, anchorSink);
    }

    public void attachDatabase(PlayerDomainRepository databaseRepository, PlayerDataLease databaseLease) {
        this.databaseRepository = databaseRepository;
        this.databaseLease = databaseLease;
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
        List<PlayerLevelDataCache.SaveTicket<UUID, PlayerLevelData>> tickets = cache.snapshotDirtyEntries();
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
        CompletableFuture<Boolean> saved = saveTicket(cache.snapshotForSave(uuid, expectedGeneration, true));
        PlayerDataLease lease = databaseLease;
        if (lease == null) {
            return saved;
        }
        return saved.whenComplete((result, throwable) -> lease.release(uuid));
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

        List<PlayerLevelDataCache.SaveTicket<UUID, PlayerLevelData>> tickets = cache.snapshotDirtyEntries();
        List<CompletableFuture<Boolean>> saveFutures = saveTickets(tickets);
        CompletableFuture<Void> savesComplete = saveFutures.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(saveFutures.toArray(CompletableFuture[]::new));
        AsyncWaits.awaitUntil(savesComplete, deadline);

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
        PlayerDomainRepository database = databaseRepository;
        if (database != null) {
            return database.listPlayers(DATABASE_DOMAIN)
                    .thenCompose(players -> loadKnownDatabaseAsync(known, players, types));
        }
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

    private CompletableFuture<List<PlayerLevelData>> loadKnownDatabaseAsync(Map<UUID, PlayerLevelData> known,
            List<UUID> players,
            Map<String, LevelTypeConfig> types) {
        List<UUID> missing = new ArrayList<>();
        for (UUID playerId : players) {
            if (!known.containsKey(playerId)) {
                missing.add(playerId);
            }
        }
        if (missing.isEmpty()) {
            return CompletableFuture.completedFuture(List.copyOf(known.values()));
        }
        List<CompletableFuture<PlayerLevelData>> futures = new ArrayList<>(missing.size());
        for (UUID playerId : missing) {
            futures.add(loadDetached(playerId, null, types));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    for (CompletableFuture<PlayerLevelData> future : futures) {
                        PlayerLevelData data = future.getNow(null);
                        if (data != null) {
                            known.put(data.uuid(), data);
                        }
                    }
                    return List.copyOf(known.values());
                });
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

        PlayerLevelDataCache.Snapshot<PlayerLevelData> current = cache.snapshot(playerId);
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
        PlayerLevelDataCache.SessionTicket<UUID> ticket = cache.beginSession(playerId, initial, false);
        if (ticket == null) {
            return CompletableFuture.completedFuture(null);
        }

        CompletableFuture<PlayerLevelData> loadResult = new CompletableFuture<>();
        PendingLoad pending = new PendingLoad(ticket.generation(), loadResult);
        pendingLoads.put(playerId, pending);
        loadResult.whenComplete((ignored, throwable) -> pendingLoads.remove(playerId, pending));

        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        CompletableFuture<PlayerLevelData> physicalLoad;
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            physicalLoad = acquireDatabaseLease(playerId)
                    .thenCompose(ignored -> repository.load(playerId, DATABASE_DOMAIN))
                    .thenApply(snapshot -> {
                        if (snapshot != null) {
                            databaseRevisions.put(playerId, snapshot.revision());
                        }
                        return installLoaded(ticket, readData(playerId, DomainPayloads.decodeYaml(snapshot), types),
                                playerName);
                    });
        } else if (asyncYamlFiles == null) {
            physicalLoad = CompletableFuture.failedFuture(
                    new IllegalStateException("AsyncYamlFiles 不可用"));
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

    private PlayerLevelData installLoaded(PlayerLevelDataCache.SessionTicket<UUID> ticket,
            PlayerLevelData loaded,
            String playerName) {
        if (cache.installLoaded(ticket, loaded) == PlayerLevelDataCache.CommitResult.COMMITTED) {
            if (playerName != null) {
                cache.mutate(ticket.key(), ticket.generation(), data -> {
                    data.name(playerName);
                    return null;
                });
            }
            return cache.activeData(ticket.key());
        }
        return cache.activeData(ticket.key());
    }

    private CompletableFuture<PlayerLevelData> loadDetached(UUID playerId,
            File file,
            Map<String, LevelTypeConfig> types) {
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            return repository.load(playerId, DATABASE_DOMAIN)
                    .thenApply(snapshot -> snapshot == null
                            ? null
                            : readData(playerId, DomainPayloads.decodeYaml(snapshot), types))
                    .exceptionally(throwable -> {
                        logLoadFailure(playerId, AsyncFailures.unwrapOnce(throwable));
                        return null;
                    });
        }
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

    private List<CompletableFuture<Boolean>> saveTickets(
            List<PlayerLevelDataCache.SaveTicket<UUID, PlayerLevelData>> tickets) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>(tickets.size());
        for (PlayerLevelDataCache.SaveTicket<UUID, PlayerLevelData> ticket : tickets) {
            futures.add(saveTicket(ticket));
        }
        return futures;
    }

    private CompletableFuture<Boolean> saveTicket(PlayerLevelDataCache.SaveTicket<UUID, PlayerLevelData> ticket) {
        if (ticket == null) {
            return CompletableFuture.completedFuture(true);
        }
        return cache.enqueueSave(ticket, current -> writeSnapshot(current.key(), current.snapshot()));
    }

    private CompletableFuture<Boolean> writeSnapshot(UUID playerId, PlayerLevelData snapshot) {
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            return writeDatabaseSnapshot(playerId, snapshot, repository);
        }
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

    private CompletableFuture<Boolean> writeDatabaseSnapshot(UUID playerId, PlayerLevelData snapshot,
            PlayerDomainRepository repository) {
        byte[] payload = YamlFiles.dump(serialize(snapshot)).getBytes(StandardCharsets.UTF_8);
        long expectedRevision = databaseRevisions.getOrDefault(playerId, 0L);
        return repository
                .save(playerId, DATABASE_DOMAIN, snapshot.name(), payload, DATABASE_SCHEMA_VERSION, expectedRevision)
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
                    logSaveFailure(playerId, failure);
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


    private CompletableFuture<Void> acquireDatabaseLease(UUID playerId) {
        PlayerDataLease lease = databaseLease;
        if (lease == null) {
            return CompletableFuture.completedFuture(null);
        }
        return PlayerDataLeases.acquireWithRetry(lease, playerId, 5);
    }


    private void logLoadFailure(UUID playerId, Throwable throwable) {
        LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.player_data_load_failed", Map.of("player", playerId), throwable);
    }

    private void logSaveFailure(UUID playerId, Throwable throwable) {
        LogTags.send(logger, LogTag.PERSISTENCE, Level.WARNING, "persistence.player_data_save_failed", Map.of("player", playerId), throwable);
    }

    private static long toLong(Object value, long fallback) {
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private int defaultLevel(LevelTypeConfig type) {
        return type == null ? 1 : type.startLevel();
    }
}
