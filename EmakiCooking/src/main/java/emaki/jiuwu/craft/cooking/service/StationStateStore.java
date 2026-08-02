package emaki.jiuwu.craft.cooking.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.async.AsyncFileService.DrainResult;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.FileScope;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.debug.DebugLoggerProvider;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlLoadException;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;

public final class StationStateStore {

    private static final String STATION_SOURCE_KEY = "station_source";
    private static final String STATION_TYPE_KEY = "station_type";
    private static final String SAVED_AT_KEY = "station_saved_at_ms";
    private static final String FORMAT_VERSION_KEY = "station_format_version";
    private static final String STATE_VERSION_KEY = "station_state_version";
    private static final String TOMBSTONE_KEY = "station_tombstone";
    private static final int FORMAT_VERSION = 1;
    private static final long INDEX_FLUSH_DELAY_SECONDS = 2L;
    private static final String INDEX_EXTENSION = ".idx";

    private final JavaPlugin plugin;
    private final FileScope fileScope;
    private final ExecutionDispatcher executionDispatcher;
    private final ThreadOwnership threadOwnership;
    private final NamespacedKey stateKey;
    private final NamespacedKey stationTypeKey;
    private final NamespacedKey stationSourceKey;
    private final NamespacedKey formatVersionKey;
    private final NamespacedKey savedAtKey;
    private final NamespacedKey stateVersionKey;
    private final NamespacedKey tombstoneKey;
    private final StationStateVersionLedger versionLedger = new StationStateVersionLedger();
    private final ConcurrentMap<StationCoordinates, ItemSourceRef> stationSources = new ConcurrentHashMap<>();
    private final ConcurrentMap<StationCoordinates, YamlSection> yamlCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<StationCoordinates, StationIndexEntry> index = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<Long, Set<StationCoordinates>>> chunkIndex = new ConcurrentHashMap<>();
    private final Set<String> dirtyIndexWorlds = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<?>> pendingOperations = ConcurrentHashMap.newKeySet();
    private final ReentrantLock pendingLock = new ReentrantLock();
    private final Condition pendingIdle = pendingLock.newCondition();
    private final AtomicBoolean indexLoaded = new AtomicBoolean(false);
    private final AtomicBoolean indexFlushScheduled = new AtomicBoolean(false);
    private volatile TaskHandle indexFlushTask;

    public StationStateStore(JavaPlugin plugin,
            FileScope fileScope,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.fileScope = fileScope;
        this.executionDispatcher = executionDispatcher;
        this.threadOwnership = threadOwnership;
        this.stateKey = new NamespacedKey(plugin, "station_state");
        this.stationTypeKey = new NamespacedKey(plugin, "station_type");
        this.stationSourceKey = new NamespacedKey(plugin, "station_source");
        this.formatVersionKey = new NamespacedKey(plugin, "station_format_version");
        this.savedAtKey = new NamespacedKey(plugin, "station_saved_at_ms");
        this.stateVersionKey = new NamespacedKey(plugin, "station_state_version");
        this.tombstoneKey = new NamespacedKey(plugin, "station_tombstone");
    }

    public Map<StationCoordinates, YamlSection> loadAll(StationType stationType) {
        Map<StationCoordinates, YamlSection> states = new ConcurrentHashMap<>();
        forEachLoadedState(stationType, states::put);
        return states.isEmpty() ? Map.of() : Map.copyOf(states);
    }

    public void forEachLoadedState(StationType stationType, BiConsumer<StationCoordinates, YamlSection> consumer) {
        if (stationType == null || consumer == null) {
            return;
        }
        ensureIndexLoaded();
        List<StationIndexEntry> typeEntries = index.values().stream()
                .filter(entry -> entry != null && entry.type() == stationType)
                .sorted(Comparator.comparing(entry -> entry.coordinates().runtimeKey()))
                .toList();
        Map<String, List<StationIndexEntry>> entriesByChunk = new LinkedHashMap<>();
        for (StationIndexEntry entry : typeEntries) {
            entriesByChunk.computeIfAbsent(chunkBucketKey(entry.coordinates()), _ -> new ArrayList<>()).add(entry);
        }
        if (entriesByChunk.isEmpty()) {
            return;
        }
        for (List<StationIndexEntry> entries : entriesByChunk.values()) {
            trackOperation(runLoadedStateBatch(stationType, entries, consumer)
                    .exceptionally(throwable -> {
                        plugin.getLogger().warning("Station restore batch failed: " + rootCauseMessage(throwable));
                        return null;
                    }));
        }
    }

    public YamlSection load(StationCoordinates coordinates) {
        if (coordinates == null) {
            return null;
        }
        requireLocationOwnership(coordinates.location(0, 0, 0));
        ensureIndexLoaded();
        StoredState pdc = readPdcCandidate(coordinates);
        StoredState yaml = readYamlCandidate(coordinates);
        StoredState persisted = latestState(pdc, yaml, readTombstoneCandidate(coordinates));
        StationStateVersionLedger.Mutation inMemory = versionLedger.currentMutation(coordinates);
        if (inMemory != null
                && (persisted == null
                || inMemory.version() > persisted.version()
                || (inMemory.version() == persisted.version() && inMemory.tombstone()))) {
            if (inMemory.tombstone() || persisted == null || inMemory.version() > persisted.version()) {
                debugStation("station.state_load", Map.of(
                        "station", coordinates.runtimeKey(),
                        "result", "null_in_memory_ahead",
                        "in_memory_version", inMemory.version(),
                        "in_memory_tombstone", inMemory.tombstone(),
                        "persisted_version", persisted == null ? "none" : persisted.version(),
                        "pdc_present", pdc != null,
                        "yaml_present", yaml != null
                ));
                return null;
            }
        }
        if (persisted == null) {
            StationIndexEntry existing = index.get(coordinates);
            if (existing != null && existing.backend() == StationStorageBackend.BLOCK_PDC && backendForCurrentBlock(coordinates) == StationStorageBackend.YAML_FALLBACK) {
                removeIndex(coordinates, true);
            }
            debugStation("station.state_load", Map.of(
                    "station", coordinates.runtimeKey(),
                    "result", "null_no_persisted_state",
                    "in_memory_version", inMemory == null ? "none" : inMemory.version(),
                    "in_memory_tombstone", inMemory != null && inMemory.tombstone(),
                    "persisted_version", "none",
                    "pdc_present", false,
                    "yaml_present", false
            ));
            return null;
        }

        versionLedger.observe(coordinates, persisted.version(), persisted.tombstone());
        if (persisted.tombstone() || persisted.state() == null) {
            yamlCache.remove(coordinates);
            removeIndex(coordinates, true);
            debugStation("station.state_load", Map.of(
                    "station", coordinates.runtimeKey(),
                    "result", "null_tombstoned",
                    "in_memory_version", versionLedger.currentVersion(coordinates),
                    "in_memory_tombstone", versionLedger.isTombstoned(coordinates),
                    "persisted_version", persisted.version(),
                    "pdc_present", pdc != null,
                    "yaml_present", yaml != null
            ));
            return null;
        }

        YamlSection state = persisted.state();
        debugStation("station.state_load", Map.of(
                "station", coordinates.runtimeKey(),
                "result", "loaded",
                "in_memory_version", versionLedger.currentVersion(coordinates),
                "in_memory_tombstone", false,
                "persisted_version", persisted.version(),
                "pdc_present", pdc != null,
                "yaml_present", yaml != null
        ));
        rememberStationSource(coordinates, stationSource(state));
        StationType type = stationType(state);
        if (persisted.backend() == StationStorageBackend.BLOCK_PDC) {
            recordIndex(coordinates, type, StationStorageBackend.BLOCK_PDC, stationSource(state), savedAt(state), true);
            if (yaml != null && yaml.version() <= persisted.version()) {
                archiveYamlIfUnchangedAsync(coordinates);
            }
            return state;
        }
        if (tryMigrateYamlToPdc(coordinates, state)) {
            recordIndex(coordinates, type, StationStorageBackend.BLOCK_PDC, stationSource(state), savedAt(state), true);
        } else {
            recordIndex(coordinates, type, StationStorageBackend.YAML_FALLBACK, stationSource(state), savedAt(state), true);
        }
        return state;
    }

    public void save(StationCoordinates coordinates, Map<String, Object> state) {
        saveAsync(coordinates, state);
    }

    public void delete(StationCoordinates coordinates) {
        deleteAsync(coordinates);
    }

    public CompletableFuture<Boolean> saveAsync(StationCoordinates coordinates, Map<String, Object> state) {
        if (coordinates == null || state == null || state.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        long mutationVersion = versionLedger.beginSave(coordinates);
        Map<String, Object> stateWithMetadata = stateWithMetadata(coordinates, state, mutationVersion, false);
        YamlSection section = new MapYamlSection(stateWithMetadata);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        debugStation("station.state_save_begin", Map.of(
                "station", coordinates.runtimeKey(),
                "version", mutationVersion,
                "backend_hint", stationType(section) == null ? "unknown" : stationType(section).folderName()
        ));
        future.whenComplete((success, throwable) -> debugStation("station.state_save_result", Map.of(
                "station", coordinates.runtimeKey(),
                "version", mutationVersion,
                "success", Boolean.TRUE.equals(success),
                "still_current", versionLedger.isCurrentSave(coordinates, mutationVersion),
                "error", throwable == null ? "" : rootCauseMessage(throwable)
        )));
        runOnBlockThread(coordinates, () -> {
            if (!versionLedger.isCurrentSave(coordinates, mutationVersion)) {
                future.complete(false);
                return;
            }
            if (tryWritePdcState(coordinates, section, mutationVersion)) {
                if (versionLedger.isCurrentSave(coordinates, mutationVersion)) {
                    yamlCache.remove(coordinates);
                    recordIndex(coordinates, stationType(section), StationStorageBackend.BLOCK_PDC, stationSource(section), savedAt(section), true);
                    archiveYamlAsync(coordinates, mutationVersion);
                    future.complete(true);
                } else {
                    future.complete(false);
                }
                return;
            }
            if (!versionLedger.isCurrentSave(coordinates, mutationVersion)) {
                future.complete(false);
                return;
            }
            saveYamlFallbackAsync(coordinates, stateWithMetadata, mutationVersion)
                    .whenComplete((success, throwable) -> {
                        if (throwable != null) {
                            future.completeExceptionally(throwable);
                            return;
                        }
                        future.complete(Boolean.TRUE.equals(success));
                    });
        }, future);
        return trackOperation(future.exceptionally(throwable -> {
            plugin.getLogger().warning("Async save failed for station " + coordinates.runtimeKey() + ": " + rootCauseMessage(throwable));
            return false;
        }));
    }

    public CompletableFuture<Boolean> deleteAsync(StationCoordinates coordinates) {
        if (coordinates == null) {
            return CompletableFuture.completedFuture(false);
        }
        long mutationVersion = versionLedger.beginDelete(coordinates);
        debugStation("station.state_delete_begin", Map.of(
                "station", coordinates.runtimeKey(),
                "version", mutationVersion
        ));
        CompletableFuture<Boolean> result = writeTombstoneAsync(coordinates, mutationVersion).thenCompose(persisted -> {
            if (!Boolean.TRUE.equals(persisted) || !versionLedger.isCurrentDelete(coordinates, mutationVersion)) {
                return CompletableFuture.completedFuture(false);
            }
            stationSources.remove(coordinates);
            yamlCache.remove(coordinates);
            removeIndex(coordinates, true);
            CompletableFuture<Boolean> pdcFuture = new CompletableFuture<>();
            runOnBlockThread(coordinates, () -> pdcFuture.complete(removePdcState(coordinates, mutationVersion)), pdcFuture);
            CompletableFuture<Boolean> yamlFuture = deleteYamlFallbackAsync(coordinates, mutationVersion);
            return CompletableFuture.allOf(
                    pdcFuture.exceptionally(_ -> false),
                    yamlFuture.exceptionally(_ -> false)
            ).thenApply(_ -> true);
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Async delete failed for station " + coordinates.runtimeKey() + ": " + rootCauseMessage(throwable));
            return false;
        });
        result.whenComplete((success, throwable) -> debugStation("station.state_delete_result", Map.of(
                "station", coordinates.runtimeKey(),
                "version", mutationVersion,
                "success", Boolean.TRUE.equals(success),
                "error", throwable == null ? "" : rootCauseMessage(throwable)
        )));
        return trackOperation(result);
    }

    public CompletableFuture<Void> waitForIdle() {
        CompletableFuture<?>[] operations = pendingOperations.toArray(CompletableFuture[]::new);
        CompletableFuture<Void> pending = operations.length == 0
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(operations);
        return pending.exceptionally(throwable -> null)
                .thenCompose(_ -> flushDirtyIndexesAsync())
                .thenCompose(_ -> fileScope == null
                        ? CompletableFuture.completedFuture(null)
                        : fileScope.waitForIdle());
    }

    public DrainResult sealAndDrain(long timeout, TimeUnit unit) {
        long timeoutNanos = Math.max(1L, unit.toNanos(timeout));
        long deadline = System.nanoTime() + timeoutNanos;
        cancelIndexFlushTask();
        trackOperation(flushDirtyIndexesAsync());
        boolean operationsDrained = awaitPendingOperations(Math.max(1L, timeoutNanos * 4L / 5L));
        cancelIndexFlushTask();
        trackOperation(flushDirtyIndexesAsync());

        long remainingNanos = Math.max(1L, deadline - System.nanoTime());
        DrainResult fileResult = fileScope == null
                ? new DrainResult(true, 0, List.of())
                : fileScope.sealAndDrain(remainingNanos, TimeUnit.NANOSECONDS);
        boolean finalOperationsDrained = awaitPendingOperations(Math.max(1L, deadline - System.nanoTime()));

        if (operationsDrained && finalOperationsDrained) {
            return fileResult;
        }
        List<Throwable> failures = new ArrayList<>(fileResult.failures());
        failures.add(new IllegalStateException("Station operations did not drain before shutdown"));
        return new DrainResult(false, fileResult.pendingOperations() + pendingOperations.size(), failures);
    }

    public void rememberStationSource(StationCoordinates coordinates, ItemSourceRef stationSource) {
        if (coordinates == null || stationSource == null) {
            return;
        }
        String shorthand = ItemSourceUtil.toShorthand(stationSource);
        if (shorthand == null || shorthand.isBlank()) {
            return;
        }
        stationSources.put(coordinates, stationSource);
    }

    public ItemSourceRef rememberedStationSource(StationCoordinates coordinates) {
        return coordinates == null ? null : stationSources.get(coordinates);
    }

    public ItemSourceRef stationSource(YamlSection state) {
        if (state == null) {
            return null;
        }
        return ItemSourceUtil.parse(state.getString(STATION_SOURCE_KEY, ""));
    }

    public StorageInspection inspect(Block block) {
        if (block == null) {
            return StorageInspection.empty();
        }
        requireLocationOwnership(block.getLocation());
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        YamlSection pdcState = readPdcState(coordinates);
        YamlSection yamlState = readYamlState(coordinates);
        YamlSection state = pdcState == null ? yamlState : pdcState;
        TileState tileState = tileStateOf(block, false);
        StationIndexEntry entry = index.get(coordinates);
        StationType stateType = state == null ? null : stationType(state);
        ItemSourceRef source = state == null ? null : stationSource(state);
        String sourceText = source == null ? "" : ItemSourceUtil.toShorthand(source);
        return new StorageInspection(
                coordinates,
                block.getType().getKey().toString(),
                block.getState().getClass().getSimpleName(),
                tileState != null,
                tileState == null ? StationStorageBackend.YAML_FALLBACK : StationStorageBackend.BLOCK_PDC,
                pdcState != null,
                yamlState != null,
                entry != null,
                entry == null ? null : entry.backend(),
                stateType,
                sourceText == null ? "" : sourceText,
                state != null
        );
    }

    public boolean isIndexed(StationCoordinates coordinates) {
        ensureIndexLoaded();
        return coordinates != null && index.containsKey(coordinates);
    }

    public StationType indexedStationType(StationCoordinates coordinates) {
        ensureIndexLoaded();
        StationIndexEntry entry = coordinates == null ? null : index.get(coordinates);
        return entry == null ? null : entry.type();
    }

    public StationStorageBackend indexedBackend(StationCoordinates coordinates) {
        ensureIndexLoaded();
        StationIndexEntry entry = coordinates == null ? null : index.get(coordinates);
        return entry == null ? null : entry.backend();
    }

    public boolean hasLegacyYaml(StationCoordinates coordinates) {
        return coordinates != null && Files.exists(pathFor(coordinates));
    }

    public StationStorageBackend backendFor(Block block) {
        if (block == null) {
            return StationStorageBackend.YAML_FALLBACK;
        }
        requireLocationOwnership(block.getLocation());
        return tileStateOf(block, false) == null ? StationStorageBackend.YAML_FALLBACK : StationStorageBackend.BLOCK_PDC;
    }

    public CompletableFuture<ReindexReport> reindexAsync() {
        ensureIndexLoaded();
        dirtyIndexWorlds.addAll(resetIndexForRebuild());
        return scanLegacyYamlAsync().thenCompose(legacyCount -> scanLoadedPdcStationsAsync()
                .thenCompose(pdcCount -> flushDirtyIndexesAsync().thenApply(_ -> new ReindexReport(legacyCount, pdcCount, index.size()))));
    }

    public Set<StationCoordinates> indexedCoordinatesInChunk(World world, int chunkX, int chunkZ) {
        if (world == null) {
            return Set.of();
        }
        ensureIndexLoaded();
        ConcurrentMap<Long, Set<StationCoordinates>> worldIndex = chunkIndex.get(world.getName());
        if (worldIndex == null) {
            return Set.of();
        }
        Set<StationCoordinates> coordinates = worldIndex.get(chunkKey(chunkX, chunkZ));
        return coordinates == null || coordinates.isEmpty() ? Set.of() : Set.copyOf(coordinates);
    }

    private CompletableFuture<Void> runLoadedStateBatch(StationType stationType,
            List<StationIndexEntry> entries,
            BiConsumer<StationCoordinates, YamlSection> consumer) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                consumeLoadedStateBatch(stationType, entries, consumer);
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        };
        executionDispatcher.submitGlobal(plugin, () -> entries == null || entries.isEmpty()
                        ? null
                        : chunkCenterLocation(entries.getFirst().coordinates()))
                .whenComplete((location, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                        return;
                    }
                    if (location == null) {
                        future.completeExceptionally(new RejectedExecutionException(
                                "Station restore target region is unavailable"));
                        return;
                    }
                    try {
                        TaskHandle handle = executionDispatcher.runAtLocation(plugin, location, task);
                        if (handle == null) {
                            future.completeExceptionally(new RejectedExecutionException(
                                    "Location dispatcher rejected station restore"));
                        }
                    } catch (Throwable error) {
                        future.completeExceptionally(error);
                    }
                });
        return future;
    }

    private void consumeLoadedStateBatch(StationType stationType,
            List<StationIndexEntry> entries,
            BiConsumer<StationCoordinates, YamlSection> consumer) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (StationIndexEntry entry : entries) {
            StationCoordinates coordinates = entry.coordinates();
            if (!isChunkLoaded(coordinates)) {
                continue;
            }
            YamlSection state = load(coordinates);
            if (state == null) {
                reportLostBlockEntityReplaced(entry);
                continue;
            }
            if (!stationType.folderName().equalsIgnoreCase(state.getString(STATION_TYPE_KEY, ""))) {
                continue;
            }
            consumer.accept(coordinates, state);
        }
    }

    private Location chunkCenterLocation(StationCoordinates coordinates) {
        if (coordinates == null || Texts.isBlank(coordinates.world())) {
            return null;
        }
        World world = Bukkit.getWorld(coordinates.world());
        if (world == null) {
            return null;
        }
        int chunkX = coordinates.x() >> 4;
        int chunkZ = coordinates.z() >> 4;
        return new Location(world, (chunkX << 4) + 8D, 0D, (chunkZ << 4) + 8D);
    }

    private void reportLostBlockEntityReplaced(StationIndexEntry entry) {
        if (entry == null || entry.backend() != StationStorageBackend.BLOCK_PDC) {
            return;
        }
        if (backendForCurrentBlock(entry.coordinates()) == StationStorageBackend.YAML_FALLBACK) {
            plugin.getLogger().warning("Station restore report: lost_block_entity_replaced type=" + entry.type().folderName()
                    + " coordinate=" + entry.coordinates().runtimeKey());
        }
    }

    private <T> CompletableFuture<T> trackOperation(CompletableFuture<T> future) {
        if (future == null) {
            return CompletableFuture.completedFuture(null);
        }
        pendingOperations.add(future);
        future.whenComplete((_, _) -> {
            pendingOperations.remove(future);
            pendingLock.lock();
            try {
                if (pendingOperations.isEmpty()) {
                    pendingIdle.signalAll();
                }
            } finally {
                pendingLock.unlock();
            }
        });
        return future;
    }

    private boolean awaitPendingOperations(long timeoutNanos) {
        long remainingNanos = Math.max(0L, timeoutNanos);
        pendingLock.lock();
        try {
            while (!pendingOperations.isEmpty() && remainingNanos > 0L) {
                try {
                    remainingNanos = pendingIdle.awaitNanos(remainingNanos);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return pendingOperations.isEmpty();
        } finally {
            pendingLock.unlock();
        }
    }

    private void cancelIndexFlushTask() {
        TaskHandle task = indexFlushTask;
        indexFlushTask = null;
        indexFlushScheduled.set(false);
        if (task != null) {
            task.cancel();
        }
    }

    private CompletableFuture<Boolean> saveYamlFallbackAsync(StationCoordinates coordinates, Map<String, Object> state, long mutationVersion) {
        YamlSection section = new MapYamlSection(state);
        StationType type = stationType(section);
        ItemSourceRef source = stationSource(section);
        long savedAt = savedAt(section);
        if (fileScope == null) {
            return CompletableFuture.completedFuture(trySaveYamlFallback(coordinates, state, mutationVersion));
        }
        Path path = pathFor(coordinates);
        AtomicBoolean wrote = new AtomicBoolean(false);
        return fileScope.write(path, "station-yaml-save:" + coordinates.runtimeKey(), () -> {
            if (!versionLedger.isCurrentSave(coordinates, mutationVersion)) {
                return;
            }
            try {
                YamlFiles.save(path.toFile(), state);
                wrote.set(true);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).thenApply(_ -> {
            if (!wrote.get() || !versionLedger.isCurrentSave(coordinates, mutationVersion)) {
                return false;
            }
            yamlCache.put(coordinates, section.copy());
            recordIndex(coordinates, type, StationStorageBackend.YAML_FALLBACK, source, savedAt, true);
            return true;
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("YAML fallback save failed for station " + coordinates.runtimeKey() + ": " + rootCauseMessage(throwable));
            return false;
        });
    }

    private boolean trySaveYamlFallback(StationCoordinates coordinates, Map<String, Object> state, long mutationVersion) {
        if (!versionLedger.isCurrentSave(coordinates, mutationVersion)) {
            return false;
        }
        YamlSection section = new MapYamlSection(state);
        try {
            YamlFiles.save(pathFor(coordinates).toFile(), state);
            if (!versionLedger.isCurrentSave(coordinates, mutationVersion)) {
                return false;
            }
            yamlCache.put(coordinates, section.copy());
            recordIndex(coordinates, stationType(section), StationStorageBackend.YAML_FALLBACK, stationSource(section), savedAt(section), true);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save YAML fallback state " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return false;
        }
    }

    private CompletableFuture<Boolean> writeTombstoneAsync(StationCoordinates coordinates, long mutationVersion) {
        if (coordinates == null || !versionLedger.isCurrentDelete(coordinates, mutationVersion)) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, Object> tombstone = stateWithMetadata(coordinates, Map.of(), mutationVersion, true);
        Path path = tombstonePathFor(coordinates);
        if (fileScope == null) {
            return CompletableFuture.completedFuture(tryWriteTombstone(path, coordinates, mutationVersion, tombstone));
        }
        AtomicBoolean written = new AtomicBoolean(false);
        return fileScope.write(path, "station-tombstone-save:" + coordinates.runtimeKey(), () -> {
            if (!versionLedger.isCurrentDelete(coordinates, mutationVersion)) {
                return;
            }
            try {
                YamlFiles.save(path.toFile(), tombstone);
                written.set(true);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).thenApply(_ -> written.get() && versionLedger.isCurrentDelete(coordinates, mutationVersion));
    }

    private boolean tryWriteTombstone(Path path,
            StationCoordinates coordinates,
            long mutationVersion,
            Map<String, Object> tombstone) {
        if (!versionLedger.isCurrentDelete(coordinates, mutationVersion)) {
            return false;
        }
        try {
            YamlFiles.save(path.toFile(), tombstone);
            return versionLedger.isCurrentDelete(coordinates, mutationVersion);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to persist station tombstone " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return false;
        }
    }

    private CompletableFuture<Boolean> deleteYamlFallbackAsync(StationCoordinates coordinates, long mutationVersion) {
        if (fileScope == null) {
            Path path = pathFor(coordinates);
            try {
                if (!versionLedger.isCurrentDelete(coordinates, mutationVersion)) {
                    return CompletableFuture.completedFuture(false);
                }
                Files.deleteIfExists(path);
                cleanupParents(path.getParent());
                return CompletableFuture.completedFuture(true);
            } catch (IOException exception) {
                CompletableFuture<Boolean> failed = new CompletableFuture<>();
                failed.completeExceptionally(exception);
                return failed;
            }
        }
        Path path = pathFor(coordinates);
        AtomicBoolean deleted = new AtomicBoolean(false);
        return fileScope.write(path, "station-yaml-delete:" + coordinates.runtimeKey(), () -> {
            if (!versionLedger.isCurrentDelete(coordinates, mutationVersion)) {
                return;
            }
            try {
                Files.deleteIfExists(path);
                cleanupParents(path.getParent());
                deleted.set(true);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).thenApply(_ -> deleted.get() && versionLedger.isCurrentDelete(coordinates, mutationVersion)).exceptionally(throwable -> {
            plugin.getLogger().warning("YAML fallback delete failed for station " + coordinates.runtimeKey() + ": " + rootCauseMessage(throwable));
            return false;
        });
    }

    private YamlSection readPdcState(StationCoordinates coordinates) {
        StoredState candidate = latestState(readPdcCandidate(coordinates), readTombstoneCandidate(coordinates));
        return candidate == null || candidate.tombstone() ? null : candidate.state();
    }

    private StoredState readPdcCandidate(StationCoordinates coordinates) {
        if (coordinates == null) {
            return null;
        }
        TileState tileState = tileStateOf(coordinates.block(), false);
        if (tileState == null) {
            return null;
        }
        PersistentDataContainer container = tileState.getPersistentDataContainer();
        String payload = container.get(stateKey, PersistentDataType.STRING);
        Long storedVersion = container.get(stateVersionKey, PersistentDataType.LONG);
        Byte storedTombstone = container.get(tombstoneKey, PersistentDataType.BYTE);
        boolean pdcTombstone = storedTombstone != null && storedTombstone != 0;
        if (Texts.isBlank(payload)) {
            return pdcTombstone
                    ? new StoredState(null, storedVersion == null ? 0L : storedVersion, true, StationStorageBackend.BLOCK_PDC)
                    : null;
        }
        try {
            YamlSection section = normalizeLoadedState(coordinates, YamlFiles.load(payload));
            long version = Math.max(storedVersion == null ? 0L : storedVersion, stateVersion(section));
            return new StoredState(section, version, pdcTombstone || tombstone(section), StationStorageBackend.BLOCK_PDC);
        } catch (YamlLoadException exception) {
            plugin.getLogger().warning("Failed to decode PDC station state " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return pdcTombstone
                    ? new StoredState(null, storedVersion == null ? 0L : storedVersion, true, StationStorageBackend.BLOCK_PDC)
                    : null;
        }
    }

    private YamlSection readYamlState(StationCoordinates coordinates) {
        StoredState candidate = latestState(readYamlCandidate(coordinates), readTombstoneCandidate(coordinates));
        return candidate == null || candidate.tombstone() ? null : candidate.state();
    }

    private StoredState readYamlCandidate(StationCoordinates coordinates) {
        if (coordinates == null) {
            return null;
        }
        YamlSection cached = yamlCache.get(coordinates);
        if (cached != null) {
            YamlSection copy = cached.copy();
            return new StoredState(copy, stateVersion(copy), tombstone(copy), StationStorageBackend.YAML_FALLBACK);
        }
        Path file = pathFor(coordinates);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            YamlSection state = normalizeLoadedState(coordinates, YamlFiles.load(file.toFile()));
            if (state == null) {
                return null;
            }
            yamlCache.put(coordinates, state.copy());
            return new StoredState(state, stateVersion(state), tombstone(state), StationStorageBackend.YAML_FALLBACK);
        } catch (YamlLoadException exception) {
            if (!causedByMissingFile(exception)) {
                throw exception;
            }
            return null;
        }
    }

    private StoredState readTombstoneCandidate(StationCoordinates coordinates) {
        if (coordinates == null) {
            return null;
        }
        Path path = tombstonePathFor(coordinates);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            YamlSection state = normalizeLoadedState(coordinates, YamlFiles.load(path.toFile()));
            if (state == null || !tombstone(state)) {
                return null;
            }
            return new StoredState(null, stateVersion(state), true, null);
        } catch (YamlLoadException exception) {
            long fallbackVersion;
            try {
                fallbackVersion = Files.getLastModifiedTime(path).toMillis();
            } catch (IOException ignored) {
                fallbackVersion = System.currentTimeMillis();
            }
            plugin.getLogger().warning("Failed to decode station tombstone " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return new StoredState(null, fallbackVersion, true, null);
        }
    }

    private StoredState latestState(StoredState... candidates) {
        StoredState selected = null;
        if (candidates == null) {
            return null;
        }
        for (StoredState candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            if (selected == null
                    || candidate.version() > selected.version()
                    || (candidate.version() == selected.version() && candidate.tombstone() && !selected.tombstone())
                    || (candidate.version() == selected.version()
                    && candidate.tombstone() == selected.tombstone()
                    && candidate.backend() == StationStorageBackend.BLOCK_PDC
                    && selected.backend() != StationStorageBackend.BLOCK_PDC)) {
                selected = candidate;
            }
        }
        return selected;
    }

    private boolean tryWritePdcState(StationCoordinates coordinates, YamlSection state, long mutationVersion) {
        if (coordinates == null || state == null || state.isEmpty() || !versionLedger.isCurrentSave(coordinates, mutationVersion)) {
            return false;
        }
        Block block = coordinates.block();
        TileState tileState = tileStateOf(block, true);
        if (tileState == null) {
            return false;
        }
        PersistentDataContainer container = tileState.getPersistentDataContainer();
        String stationType = state.getString(STATION_TYPE_KEY, "");
        String stationSource = state.getString(STATION_SOURCE_KEY, "");
        long savedAt = savedAt(state);
        container.set(stateKey, PersistentDataType.STRING, YamlFiles.dump(state.asMap()));
        if (Texts.isNotBlank(stationType)) {
            container.set(stationTypeKey, PersistentDataType.STRING, stationType);
        }
        if (Texts.isNotBlank(stationSource)) {
            container.set(stationSourceKey, PersistentDataType.STRING, stationSource);
        }
        container.set(formatVersionKey, PersistentDataType.INTEGER, FORMAT_VERSION);
        container.set(savedAtKey, PersistentDataType.LONG, savedAt);
        container.set(stateVersionKey, PersistentDataType.LONG, mutationVersion);
        container.set(tombstoneKey, PersistentDataType.BYTE, (byte) 0);
        try {
            if (!tileState.update(false, false)) {
                return false;
            }
            return versionLedger.isCurrentSave(coordinates, mutationVersion);
        } catch (Exception exception) {
            plugin.getLogger().warning("PDC station save failed for " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return false;
        }
    }

    private boolean removePdcState(StationCoordinates coordinates, long mutationVersion) {
        if (coordinates == null || !versionLedger.isCurrentDelete(coordinates, mutationVersion)) {
            return false;
        }
        TileState tileState = tileStateOf(coordinates.block(), true);
        if (tileState == null) {
            return false;
        }
        PersistentDataContainer container = tileState.getPersistentDataContainer();
        container.remove(stateKey);
        container.remove(stationTypeKey);
        container.remove(stationSourceKey);
        container.set(formatVersionKey, PersistentDataType.INTEGER, FORMAT_VERSION);
        container.set(savedAtKey, PersistentDataType.LONG, System.currentTimeMillis());
        container.set(stateVersionKey, PersistentDataType.LONG, mutationVersion);
        container.set(tombstoneKey, PersistentDataType.BYTE, (byte) 1);
        try {
            if (!tileState.update(false, false)) {
                return false;
            }
            return versionLedger.isCurrentDelete(coordinates, mutationVersion);
        } catch (Exception exception) {
            plugin.getLogger().warning("PDC station delete failed for " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return false;
        }
    }

    private boolean tryMigrateYamlToPdc(StationCoordinates coordinates, YamlSection yamlState) {
        if (coordinates == null || yamlState == null || yamlState.isEmpty() || versionLedger.isTombstoned(coordinates)) {
            return false;
        }
        if (backendForCurrentBlock(coordinates) == StationStorageBackend.YAML_FALLBACK) {
            debugStation("station.migrate_skipped", Map.of(
                    "station", coordinates.runtimeKey(),
                    "reason", "block_is_not_tile_state"
            ));
            return false;
        }
        StationStateVersionLedger.Mutation previous = versionLedger.currentMutation(coordinates);
        long mutationVersion = versionLedger.beginSave(coordinates);
        Map<String, Object> stateWithVersion = new LinkedHashMap<>(yamlState.asMap());
        stateWithVersion.put(STATE_VERSION_KEY, mutationVersion);
        stateWithVersion.put(TOMBSTONE_KEY, false);
        YamlSection versionedState = new MapYamlSection(stateWithVersion);
        if (tryWritePdcState(coordinates, versionedState, mutationVersion)) {
            archiveYamlAsync(coordinates, mutationVersion);
            yamlCache.remove(coordinates);
            debugStation("station.migrate_ok", Map.of(
                    "station", coordinates.runtimeKey(),
                    "version", mutationVersion
            ));
            return true;
        }
        boolean rolledBack = versionLedger.abandonMutation(coordinates, mutationVersion, previous);
        debugStation("station.migrate_failed", Map.of(
                "station", coordinates.runtimeKey(),
                "version", mutationVersion,
                "previous_version", previous == null ? "none" : previous.version(),
                "rolled_back", rolledBack
        ));
        return false;
    }

    private void archiveYamlIfUnchangedAsync(StationCoordinates coordinates) {
        if (coordinates == null || versionLedger.isTombstoned(coordinates)) {
            return;
        }
        archiveYamlAsync(coordinates, versionLedger.currentVersion(coordinates), false);
    }

    private void archiveYamlAsync(StationCoordinates coordinates, long mutationVersion) {
        archiveYamlAsync(coordinates, mutationVersion, true);
    }

    private void archiveYamlAsync(StationCoordinates coordinates, long mutationVersion, boolean requireCurrentSave) {
        if (coordinates == null || !canArchiveYaml(coordinates, mutationVersion, requireCurrentSave)) {
            return;
        }
        Path source = pathFor(coordinates);
        if (!Files.exists(source)) {
            return;
        }
        Path target = legacyBackupPathFor(coordinates);
        Runnable task = () -> {
            if (!canArchiveYaml(coordinates, mutationVersion, requireCurrentSave)) {
                return;
            }
            try {
                if (!Files.exists(source)) {
                    return;
                }
                YamlFiles.ensureDirectory(target.getParent());
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                cleanupParents(source.getParent());
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        };
        if (fileScope == null) {
            try {
                task.run();
            } catch (CompletionException exception) {
                plugin.getLogger().warning("Failed to archive legacy station YAML " + coordinates.runtimeKey() + ": " + rootCauseMessage(exception));
            }
            return;
        }
        fileScope.write(source, "station-yaml-archive:" + coordinates.runtimeKey(), task)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to archive legacy station YAML " + coordinates.runtimeKey() + ": " + rootCauseMessage(throwable));
                    return null;
                });
    }

    private boolean canArchiveYaml(StationCoordinates coordinates, long mutationVersion, boolean requireCurrentSave) {
        if (coordinates == null) {
            return false;
        }
        if (requireCurrentSave) {
            return versionLedger.isCurrentSave(coordinates, mutationVersion);
        }
        return !versionLedger.isTombstoned(coordinates) && versionLedger.currentVersion(coordinates) == mutationVersion;
    }

    private Map<String, Object> stateWithMetadata(StationCoordinates coordinates, Map<String, Object> state, long mutationVersion, boolean tombstone) {
        Map<String, Object> copy = new LinkedHashMap<>(MapYamlSection.normalizeMap(state));
        copy.putIfAbsent("world", coordinates.world());
        copy.putIfAbsent("x", coordinates.x());
        copy.putIfAbsent("y", coordinates.y());
        copy.putIfAbsent("z", coordinates.z());
        copy.put(SAVED_AT_KEY, System.currentTimeMillis());
        copy.put(FORMAT_VERSION_KEY, FORMAT_VERSION);
        copy.put(STATE_VERSION_KEY, mutationVersion);
        copy.put(TOMBSTONE_KEY, tombstone);
        ItemSourceRef explicitSource = ItemSourceUtil.parse(copy.get(STATION_SOURCE_KEY));
        if (explicitSource != null) {
            rememberStationSource(coordinates, explicitSource);
            copy.put(STATION_SOURCE_KEY, ItemSourceUtil.toShorthand(explicitSource));
            return copy;
        }
        ItemSourceRef remembered = rememberedStationSource(coordinates);
        String shorthand = ItemSourceUtil.toShorthand(remembered);
        if (Texts.isNotBlank(shorthand)) {
            copy.put(STATION_SOURCE_KEY, shorthand);
        }
        return copy;
    }

    private YamlSection normalizeLoadedState(StationCoordinates coordinates, YamlSection state) {
        if (state == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(state.asMap());
        if (coordinates != null) {
            copy.putIfAbsent("world", coordinates.world());
            copy.putIfAbsent("x", coordinates.x());
            copy.putIfAbsent("y", coordinates.y());
            copy.putIfAbsent("z", coordinates.z());
        }
        copy.putIfAbsent(FORMAT_VERSION_KEY, FORMAT_VERSION);
        copy.putIfAbsent(STATE_VERSION_KEY, 0L);
        copy.putIfAbsent(TOMBSTONE_KEY, false);
        return new MapYamlSection(copy);
    }

    private StationStorageBackend backendForCurrentBlock(StationCoordinates coordinates) {
        return tileStateOf(coordinates == null ? null : coordinates.block(), false) == null
                ? StationStorageBackend.YAML_FALLBACK
                : StationStorageBackend.BLOCK_PDC;
    }

    private TileState tileStateOf(Block block, boolean writable) {
        if (block == null) {
            return null;
        }
        try {
            BlockState state = writable ? block.getState() : block.getState(false);
            return state instanceof TileState tileState ? tileState : null;
        } catch (NoSuchMethodError ignored) {
            BlockState state = block.getState();
            return state instanceof TileState tileState ? tileState : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private StationType stationType(YamlSection state) {
        if (state == null) {
            return null;
        }
        String raw = Texts.normalizeId(state.getString(STATION_TYPE_KEY, ""));
        if (Texts.isBlank(raw)) {
            return null;
        }
        for (StationType type : StationType.values()) {
            if (raw.equals(Texts.normalizeId(type.folderName())) || raw.equals(Texts.normalizeId(type.name()))) {
                return type;
            }
        }
        return null;
    }

    private long savedAt(YamlSection state) {
        if (state == null) {
            return 0L;
        }
        Object raw = state.get(SAVED_AT_KEY);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        if (raw != null) {
            try {
                return Long.parseLong(String.valueOf(raw).trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private long stateVersion(YamlSection state) {
        if (state == null) {
            return 0L;
        }
        Object raw = state.get(STATE_VERSION_KEY);
        if (raw instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        try {
            return Math.max(0L, Long.parseLong(Texts.toStringSafe(raw).trim()));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private boolean tombstone(YamlSection state) {
        if (state == null) {
            return false;
        }
        Object raw = state.get(TOMBSTONE_KEY);
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        return raw != null && Boolean.parseBoolean(String.valueOf(raw).trim());
    }

    private void ensureIndexLoaded() {
        if (indexLoaded.get()) {
            return;
        }
        synchronized (indexLoaded) {
            if (indexLoaded.get()) {
                return;
            }
            boolean loaded = loadIndexFiles();
            if (!loaded || index.isEmpty()) {
                int legacyCount = scanLegacyYamlSync();
                if (legacyCount > 0) {
                    flushDirtyIndexesAsync();
                }
            }
            indexLoaded.set(true);
        }
    }

    private boolean loadIndexFiles() {
        Path root = indexRoot();
        if (!Files.exists(root)) {
            return false;
        }
        boolean loadedAny = false;
        try (Stream<Path> stream = Files.walk(root, 1)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(INDEX_EXTENSION))
                    .toList()) {
                loadedAny = true;
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    StationIndexEntry entry = parseIndexLine(line);
                    if (entry != null) {
                        putIndexEntry(entry, false);
                        rememberStationSource(entry.coordinates(), entry.source());
                    }
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to load station index: " + exception.getMessage());
        }
        return loadedAny;
    }

    private int scanLegacyYamlSync() {
        Path root = stationsRoot();
        if (!Files.exists(root)) {
            return 0;
        }
        int count = 0;
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(this::isYamlFile)
                    .filter(path -> !path.startsWith(indexRoot()))
                    .toList()) {
                YamlSection state = YamlFiles.load(file.toFile());
                StationType type = stationType(state);
                StationCoordinates coordinates = StationCoordinates.fromSection(state);
                if (type == null || coordinates == null) {
                    continue;
                }
                YamlSection normalized = normalizeLoadedState(coordinates, state);
                StoredState selected = latestState(
                        normalized == null ? null : new StoredState(
                                normalized,
                                stateVersion(normalized),
                                tombstone(normalized),
                                StationStorageBackend.YAML_FALLBACK),
                        readTombstoneCandidate(coordinates)
                );
                if (selected == null || selected.tombstone() || selected.state() == null) {
                    continue;
                }
                normalized = selected.state();
                yamlCache.put(coordinates, normalized.copy());
                rememberStationSource(coordinates, stationSource(normalized));
                recordIndex(coordinates, type, StationStorageBackend.YAML_FALLBACK, stationSource(normalized), savedAt(normalized), true);
                count++;
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to scan legacy station YAML: " + exception.getMessage());
        }
        return count;
    }

    private CompletableFuture<Integer> scanLegacyYamlAsync() {
        if (fileScope == null) {
            return CompletableFuture.completedFuture(scanLegacyYamlSync());
        }
        return fileScope.read("station-index-scan-legacy-yaml", this::scanLegacyYamlSync)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Legacy station YAML scan failed: " + rootCauseMessage(throwable));
                    return 0;
                });
    }

    private CompletableFuture<Integer> scanLoadedPdcStationsAsync() {
        CompletableFuture<List<LoadedChunkRef>> snapshotFuture = executionDispatcher.submitGlobal(plugin, () -> {
            List<LoadedChunkRef> chunks = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    chunks.add(new LoadedChunkRef(world.getName(), chunk.getX(), chunk.getZ()));
                }
            }
            return List.copyOf(chunks);
        });
        return snapshotFuture.thenCompose(chunks -> {
            if (chunks.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }
            List<CompletableFuture<Integer>> futures = new ArrayList<>();
            for (LoadedChunkRef chunk : chunks) {
                futures.add(scanLoadedPdcChunkAsync(chunk));
            }
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .thenApply(_ -> futures.stream().mapToInt(future -> future.getNow(0)).sum());
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Loaded TileState PDC reindex failed: " + rootCauseMessage(throwable));
            return 0;
        });
    }

    private CompletableFuture<Integer> scanLoadedPdcChunkAsync(LoadedChunkRef chunkRef) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        if (chunkRef == null) {
            future.complete(0);
            return future;
        }
        try {
            World world = Bukkit.getWorld(chunkRef.worldName());
            if (world == null) {
                future.complete(0);
                return future;
            }
            Location location = new Location(world, (chunkRef.chunkX() << 4) + 8D, 0D, (chunkRef.chunkZ() << 4) + 8D);
            TaskHandle handle = executionDispatcher.runAtLocation(plugin, location, () -> {
                try {
                    if (!world.isChunkLoaded(chunkRef.chunkX(), chunkRef.chunkZ())) {
                        future.complete(0);
                        return;
                    }
                    future.complete(scanLoadedPdcChunkSync(world.getChunkAt(chunkRef.chunkX(), chunkRef.chunkZ())));
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
            if (handle == null) {
                future.completeExceptionally(new RejectedExecutionException(
                        "Location dispatcher rejected PDC scan"));
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private int scanLoadedPdcChunkSync(Chunk chunk) {
        if (chunk == null || chunk.getWorld() == null || !chunk.isLoaded()) {
            return 0;
        }
        int count = 0;
        World world = chunk.getWorld();
        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof TileState tileState)) {
                continue;
            }
            String payload = tileState.getPersistentDataContainer().get(stateKey, PersistentDataType.STRING);
            if (Texts.isBlank(payload)) {
                continue;
            }
            StationCoordinates coordinates = new StationCoordinates(world.getName(), state.getX(), state.getY(), state.getZ());
            YamlSection section = readPdcState(coordinates);
            StationType type = stationType(section);
            if (type == null) {
                continue;
            }
            recordIndex(coordinates, type, StationStorageBackend.BLOCK_PDC, stationSource(section), savedAt(section), true);
            count++;
        }
        return count;
    }

    private Set<String> resetIndexForRebuild() {
        Set<String> affectedWorlds = new LinkedHashSet<>(chunkIndex.keySet());
        for (StationCoordinates coordinates : index.keySet()) {
            if (coordinates != null && Texts.isNotBlank(coordinates.world())) {
                affectedWorlds.add(coordinates.world());
            }
        }
        index.clear();
        chunkIndex.clear();
        return affectedWorlds;
    }

    private void recordIndex(StationCoordinates coordinates,
            StationType type,
            StationStorageBackend backend,
            ItemSourceRef source,
            long savedAt,
            boolean dirty) {
        if (coordinates == null || type == null) {
            return;
        }
        StationIndexEntry entry = new StationIndexEntry(
                coordinates,
                type,
                backend == null ? StationStorageBackend.YAML_FALLBACK : backend,
                source,
                savedAt
        );
        putIndexEntry(entry, dirty);
        if (source != null) {
            rememberStationSource(coordinates, source);
        }
    }

    private void putIndexEntry(StationIndexEntry entry, boolean dirty) {
        if (entry == null || entry.coordinates() == null) {
            return;
        }
        StationIndexEntry previous = index.put(entry.coordinates(), entry);
        if (previous != null) {
            removeChunkIndex(previous.coordinates());
        }
        addChunkIndex(entry.coordinates());
        if (dirty) {
            scheduleIndexFlush(entry.coordinates().world());
        }
    }

    private void removeIndex(StationCoordinates coordinates, boolean dirty) {
        if (coordinates == null) {
            return;
        }
        StationIndexEntry removed = index.remove(coordinates);
        if (removed != null) {
            removeChunkIndex(coordinates);
        }
        if (dirty && removed != null) {
            scheduleIndexFlush(coordinates.world());
        }
    }

    private void addChunkIndex(StationCoordinates coordinates) {
        if (coordinates == null || Texts.isBlank(coordinates.world())) {
            return;
        }
        chunkIndex.computeIfAbsent(coordinates.world(), _ -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey(coordinates), _ -> ConcurrentHashMap.newKeySet())
                .add(coordinates);
    }

    private void removeChunkIndex(StationCoordinates coordinates) {
        if (coordinates == null || Texts.isBlank(coordinates.world())) {
            return;
        }
        ConcurrentMap<Long, Set<StationCoordinates>> worldIndex = chunkIndex.get(coordinates.world());
        if (worldIndex == null) {
            return;
        }
        long key = chunkKey(coordinates);
        Set<StationCoordinates> bucket = worldIndex.get(key);
        if (bucket == null) {
            return;
        }
        bucket.remove(coordinates);
        if (bucket.isEmpty()) {
            worldIndex.remove(key);
        }
        if (worldIndex.isEmpty()) {
            chunkIndex.remove(coordinates.world());
        }
    }

    private void scheduleIndexFlush(String world) {
        if (Texts.isBlank(world)) {
            return;
        }
        dirtyIndexWorlds.add(world);
        if (!indexFlushScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            indexFlushTask = executionDispatcher.runGlobalLater(plugin, () -> {
                indexFlushTask = null;
                indexFlushScheduled.set(false);
                trackOperation(flushDirtyIndexesAsync());
            }, INDEX_FLUSH_DELAY_SECONDS * 20L);
        } catch (Throwable throwable) {
            indexFlushTask = null;
            indexFlushScheduled.set(false);
            trackOperation(flushDirtyIndexesAsync());
            plugin.getLogger().warning("Failed to schedule station index flush: " + rootCauseMessage(throwable));
        }
    }

    private CompletableFuture<Void> flushDirtyIndexesAsync() {
        Set<String> worlds = new LinkedHashSet<>(dirtyIndexWorlds);
        if (worlds.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        dirtyIndexWorlds.removeAll(worlds);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String world : worlds) {
            futures.add(writeIndexWorldAsync(world));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> writeIndexWorldAsync(String world) {
        Path file = indexFileFor(world);
        List<String> lines = index.values().stream()
                .filter(entry -> entry != null && world.equals(entry.coordinates().world()))
                .sorted(Comparator.comparing(entry -> entry.coordinates().runtimeKey()))
                .map(this::formatIndexLine)
                .toList();
        Runnable task = () -> {
            try {
                if (lines.isEmpty()) {
                    Files.deleteIfExists(file);
                    cleanupParents(file.getParent());
                    return;
                }
                YamlFiles.ensureDirectory(file.getParent());
                Files.write(file, lines, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        };
        if (fileScope == null) {
            try {
                task.run();
                return CompletableFuture.completedFuture(null);
            } catch (CompletionException exception) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(exception);
                return failed;
            }
        }
        return fileScope.write(file, "station-index-save:" + world, task)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to write station index for world " + world + ": " + rootCauseMessage(throwable));
                    return null;
                });
    }

    private StationIndexEntry parseIndexLine(String line) {
        if (Texts.isBlank(line) || line.trim().startsWith("#")) {
            return null;
        }
        String[] parts = line.split(",", -1);
        if (parts.length < 6) {
            return null;
        }
        try {
            String world = parts[0];
            StationType type = resolveStationType(parts[1]);
            int x = Integer.parseInt(parts[2]);
            int y = Integer.parseInt(parts[3]);
            int z = Integer.parseInt(parts[4]);
            StationStorageBackend backend = StationStorageBackend.parse(parts[5]);
            ItemSourceRef source = parts.length >= 7 ? ItemSourceUtil.parse(parts[6]) : null;
            long savedAt = 0L;
            if (parts.length >= 8 && Texts.isNotBlank(parts[7])) {
                savedAt = Long.parseLong(parts[7]);
            }
            if (type == null || Texts.isBlank(world)) {
                return null;
            }
            return new StationIndexEntry(new StationCoordinates(world, x, y, z), type, backend, source, savedAt);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String formatIndexLine(StationIndexEntry entry) {
        String source = ItemSourceUtil.toShorthand(entry.source());
        return String.join(",",
                entry.coordinates().world(),
                entry.type().folderName(),
                String.valueOf(entry.coordinates().x()),
                String.valueOf(entry.coordinates().y()),
                String.valueOf(entry.coordinates().z()),
                entry.backend().name(),
                source == null ? "" : source,
                String.valueOf(entry.savedAtMs())
        );
    }

    private StationType resolveStationType(String raw) {
        String normalized = Texts.normalizeId(raw);
        for (StationType type : StationType.values()) {
            if (normalized.equals(Texts.normalizeId(type.folderName())) || normalized.equals(Texts.normalizeId(type.name()))) {
                return type;
            }
        }
        return null;
    }

    private void runOnBlockThread(StationCoordinates coordinates, Runnable task, CompletableFuture<?> future) {
        if (task == null) {
            return;
        }
        Location location = coordinates == null ? null : coordinates.location(0, 0, 0);
        if (location == null) {
            if (future != null) {
                future.completeExceptionally(new RejectedExecutionException(
                        "Station operation target region is unavailable"));
            }
            return;
        }
        try {
            TaskHandle handle = executionDispatcher.runAtLocation(plugin, location, () -> {
                try {
                    task.run();
                } catch (Throwable throwable) {
                    if (future != null) {
                        future.completeExceptionally(throwable);
                    }
                }
            });
            if (handle == null && future != null) {
                future.completeExceptionally(new RejectedExecutionException(
                        "Location dispatcher rejected station operation"));
            }
        } catch (Throwable throwable) {
            if (future != null) {
                future.completeExceptionally(throwable);
            }
        }
    }

    private Path pathFor(StationCoordinates coordinates) {
        return plugin.getDataFolder().toPath().resolve(coordinates.relativeDataPath());
    }

    private Path legacyBackupPathFor(StationCoordinates coordinates) {
        return plugin.getDataFolder().toPath().resolve("data").resolve("stations-legacy-backup")
                .resolve(sanitizeWorld(coordinates.world()))
                .resolve(coordinates.x() + "_" + coordinates.y() + "_" + coordinates.z() + ".yml");
    }

    private Path tombstonePathFor(StationCoordinates coordinates) {
        return plugin.getDataFolder().toPath().resolve("data").resolve("station-tombstones")
                .resolve(sanitizeWorld(coordinates.world()))
                .resolve(coordinates.x() + "_" + coordinates.y() + "_" + coordinates.z() + ".yml");
    }

    private Path stationsRoot() {
        return plugin.getDataFolder().toPath().resolve("data").resolve("stations");
    }

    private Path indexRoot() {
        return stationsRoot().resolve("index");
    }

    private Path indexFileFor(String world) {
        return indexRoot().resolve(sanitizeWorld(world) + INDEX_EXTENSION);
    }

    private String sanitizeWorld(String world) {
        String normalized = Texts.toStringSafe(world).trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
        return normalized.isBlank() ? "world" : normalized;
    }

    private boolean isChunkLoaded(StationCoordinates coordinates) {
        if (coordinates == null || Texts.isBlank(coordinates.world())) {
            return false;
        }
        World world = Bukkit.getWorld(coordinates.world());
        return world != null && world.isChunkLoaded(coordinates.x() >> 4, coordinates.z() >> 4);
    }

    private void requireLocationOwnership(Location location) {
        if (location == null || threadOwnership == null || threadOwnership.isLocationOwned(location)) {
            return;
        }
        throw new IllegalStateException("StationStateStore Bukkit state access requires location ownership");
    }

    private record LoadedChunkRef(String worldName, int chunkX, int chunkZ) {
    }

    private String chunkBucketKey(StationCoordinates coordinates) {
        if (coordinates == null) {
            return "";
        }
        return coordinates.world() + ':' + chunkKey(coordinates);
    }

    private long chunkKey(StationCoordinates coordinates) {
        return coordinates == null ? 0L : chunkKey(coordinates.x() >> 4, coordinates.z() >> 4);
    }

    private long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    private boolean isYamlFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    private boolean causedByMissingFile(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof NoSuchFileException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void cleanupParents(Path directory) throws IOException {
        Path current = directory;
        Path stationsRoot = stationsRoot();
        while (current != null && !current.equals(stationsRoot) && Files.exists(current)) {
            try (Stream<Path> entries = Files.list(current)) {
                if (entries.findAny().isPresent()) {
                    break;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof CompletionException ce && ce.getCause() != null) {
            cause = ce.getCause();
        }
        return cause == null ? "unknown" : String.valueOf(cause.getMessage());
    }

    private void debugStation(String langKey, Map<String, ?> replacements) {
        DebugLogger debugLogger = plugin instanceof DebugLoggerProvider provider ? provider.debugLogger() : null;
        if (debugLogger == null) {
            return;
        }
        debugLogger.log("station", (UUID) null, langKey, replacements);
    }

    public enum StationStorageBackend {
        BLOCK_PDC,
        YAML_FALLBACK;

        private static StationStorageBackend parse(String raw) {
            if (Texts.isBlank(raw)) {
                return YAML_FALLBACK;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return YAML_FALLBACK;
            }
        }
    }

    public record StorageInspection(StationCoordinates coordinates,
            String blockType,
            String blockStateClass,
            boolean tileState,
            StationStorageBackend currentBackend,
            boolean pdcPresent,
            boolean legacyYamlPresent,
            boolean indexed,
            StationStorageBackend indexedBackend,
            StationType stationType,
            String stationSource,
            boolean statePresent) {

        private static StorageInspection empty() {
            return new StorageInspection(null, "", "", false, StationStorageBackend.YAML_FALLBACK, false, false, false, null, null, "", false);
        }
    }

    public record ReindexReport(int legacyYamlStates, int loadedPdcStates, int totalIndexedStates) {
    }

    private record StoredState(YamlSection state,
            long version,
            boolean tombstone,
            StationStorageBackend backend) {
    }

    private record StationIndexEntry(StationCoordinates coordinates,
            StationType type,
            StationStorageBackend backend,
            ItemSourceRef source,
            long savedAtMs) {
    }
}
