package emaki.jiuwu.craft.cooking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.async.AsyncFileService.DrainResult;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.FileScope;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.debug.DebugLoggerProvider;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;

public final class StationStateStore {

    private final JavaPlugin plugin;
    private final FileScope fileScope;
    private final ExecutionDispatcher executionDispatcher;
    private final StationStateArbiter arbiter;
    private final StationStateFileStore fileStore;
    private final StationIndexRegistry indexRegistry;
    private final Set<CompletableFuture<?>> pendingOperations = ConcurrentHashMap.newKeySet();
    private final ReentrantLock pendingLock = new ReentrantLock();
    private final Condition pendingIdle = pendingLock.newCondition();

    public StationStateStore(JavaPlugin plugin,
            FileScope fileScope,
            ExecutionDispatcher executionDispatcher,
            ThreadOwnership threadOwnership) {
        this.plugin = plugin;
        this.fileScope = fileScope;
        this.executionDispatcher = executionDispatcher;
        this.arbiter = new StationStateArbiter();
        this.fileStore = new StationStateFileStore(plugin, fileScope, threadOwnership, arbiter);
        this.indexRegistry = new StationIndexRegistry(
                plugin, fileScope, executionDispatcher, fileStore, arbiter, this::trackOperation);
        this.fileStore.attachIndexRegistry(indexRegistry);
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
        Map<String, List<StationIndexEntry>> entriesByChunk = indexRegistry.entriesByChunkForType(stationType);
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
        fileStore.requireLocationOwnership(coordinates.location(0, 0, 0));
        indexRegistry.ensureIndexLoaded();
        StoredState pdc = fileStore.readPdcCandidate(coordinates);
        StoredState yaml = fileStore.readYamlCandidate(coordinates);
        StoredState persisted = arbiter.latestState(pdc, yaml, fileStore.readTombstoneCandidate(coordinates));
        StationStateVersionLedger.Mutation inMemory = arbiter.currentMutation(coordinates);
        if (arbiter.inMemoryWins(inMemory, persisted)) {
            debugStation(plugin, "station.state_load", Map.of(
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
        if (persisted == null) {
            StationIndexEntry existing = indexRegistry.entry(coordinates);
            if (existing != null && existing.backend() == StationStorageBackend.BLOCK_PDC
                    && fileStore.backendForCurrentBlock(coordinates) == StationStorageBackend.YAML_FALLBACK) {
                indexRegistry.removeIndex(coordinates, true);
            }
            debugStation(plugin, "station.state_load", Map.of(
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

        arbiter.observe(coordinates, persisted.version(), persisted.tombstone());
        if (persisted.tombstone() || persisted.state() == null) {
            fileStore.invalidateYamlCache(coordinates);
            indexRegistry.removeIndex(coordinates, true);
            debugStation(plugin, "station.state_load", Map.of(
                    "station", coordinates.runtimeKey(),
                    "result", "null_tombstoned",
                    "in_memory_version", arbiter.currentVersion(coordinates),
                    "in_memory_tombstone", arbiter.isTombstoned(coordinates),
                    "persisted_version", persisted.version(),
                    "pdc_present", pdc != null,
                    "yaml_present", yaml != null
            ));
            return null;
        }

        YamlSection state = persisted.state();
        debugStation(plugin, "station.state_load", Map.of(
                "station", coordinates.runtimeKey(),
                "result", "loaded",
                "in_memory_version", arbiter.currentVersion(coordinates),
                "in_memory_tombstone", false,
                "persisted_version", persisted.version(),
                "pdc_present", pdc != null,
                "yaml_present", yaml != null
        ));
        rememberStationSource(coordinates, stationSource(state));
        StationType type = fileStore.stationType(state);
        if (persisted.backend() == StationStorageBackend.BLOCK_PDC) {
            indexRegistry.recordIndex(coordinates, type, StationStorageBackend.BLOCK_PDC, stationSource(state), fileStore.savedAt(state), true);
            if (yaml != null && yaml.version() <= persisted.version()) {
                fileStore.archiveYamlIfUnchangedAsync(coordinates);
            }
            return state;
        }
        if (fileStore.tryMigrateYamlToPdc(coordinates, state)) {
            indexRegistry.recordIndex(coordinates, type, StationStorageBackend.BLOCK_PDC, stationSource(state), fileStore.savedAt(state), true);
        } else {
            indexRegistry.recordIndex(coordinates, type, StationStorageBackend.YAML_FALLBACK, stationSource(state), fileStore.savedAt(state), true);
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
        long mutationVersion = arbiter.beginSave(coordinates);
        Map<String, Object> stateWithMetadata = fileStore.stateWithMetadata(coordinates, state, mutationVersion, false);
        YamlSection section = new MapYamlSection(stateWithMetadata);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        debugStation(plugin, "station.state_save_begin", Map.of(
                "station", coordinates.runtimeKey(),
                "version", mutationVersion,
                "backend_hint", fileStore.stationType(section) == null ? "unknown" : fileStore.stationType(section).folderName()
        ));
        future.whenComplete((success, throwable) -> debugStation(plugin, "station.state_save_result", Map.of(
                "station", coordinates.runtimeKey(),
                "version", mutationVersion,
                "success", Boolean.TRUE.equals(success),
                "still_current", arbiter.isCurrentSave(coordinates, mutationVersion),
                "error", throwable == null ? "" : rootCauseMessage(throwable)
        )));
        runOnBlockThread(coordinates, () -> {
            if (!arbiter.isCurrentSave(coordinates, mutationVersion)) {
                future.complete(false);
                return;
            }
            if (fileStore.tryWritePdcState(coordinates, section, mutationVersion)) {
                if (arbiter.isCurrentSave(coordinates, mutationVersion)) {
                    fileStore.invalidateYamlCache(coordinates);
                    indexRegistry.recordIndex(coordinates, fileStore.stationType(section), StationStorageBackend.BLOCK_PDC, fileStore.stationSource(section), fileStore.savedAt(section), true);
                    fileStore.archiveYamlAsync(coordinates, mutationVersion);
                    future.complete(true);
                } else {
                    future.complete(false);
                }
                return;
            }
            if (!arbiter.isCurrentSave(coordinates, mutationVersion)) {
                future.complete(false);
                return;
            }
            fileStore.saveYamlFallbackAsync(coordinates, stateWithMetadata, mutationVersion)
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
        long mutationVersion = arbiter.beginDelete(coordinates);
        debugStation(plugin, "station.state_delete_begin", Map.of(
                "station", coordinates.runtimeKey(),
                "version", mutationVersion
        ));
        CompletableFuture<Boolean> result = fileStore.writeTombstoneAsync(coordinates, mutationVersion).thenCompose(persisted -> {
            if (!Boolean.TRUE.equals(persisted) || !arbiter.isCurrentDelete(coordinates, mutationVersion)) {
                return CompletableFuture.completedFuture(false);
            }
            fileStore.forgetStationSource(coordinates);
            fileStore.invalidateYamlCache(coordinates);
            indexRegistry.removeIndex(coordinates, true);
            CompletableFuture<Boolean> pdcFuture = new CompletableFuture<>();
            runOnBlockThread(coordinates, () -> pdcFuture.complete(fileStore.removePdcState(coordinates, mutationVersion)), pdcFuture);
            CompletableFuture<Boolean> yamlFuture = fileStore.deleteYamlFallbackAsync(coordinates, mutationVersion);
            return CompletableFuture.allOf(
                    pdcFuture.exceptionally(_ -> false),
                    yamlFuture.exceptionally(_ -> false)
            ).thenApply(_ -> true);
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Async delete failed for station " + coordinates.runtimeKey() + ": " + rootCauseMessage(throwable));
            return false;
        });
        result.whenComplete((success, throwable) -> debugStation(plugin, "station.state_delete_result", Map.of(
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
                .thenCompose(_ -> indexRegistry.flushDirtyIndexesAsync())
                .thenCompose(_ -> fileScope == null
                        ? CompletableFuture.completedFuture(null)
                        : fileScope.waitForIdle());
    }

    public DrainResult sealAndDrain(long timeout, TimeUnit unit) {
        long timeoutNanos = Math.max(1L, unit.toNanos(timeout));
        long deadline = System.nanoTime() + timeoutNanos;
        indexRegistry.cancelIndexFlushTask();
        trackOperation(indexRegistry.flushDirtyIndexesAsync());
        boolean operationsDrained = awaitPendingOperations(Math.max(1L, timeoutNanos * 4L / 5L));
        indexRegistry.cancelIndexFlushTask();
        trackOperation(indexRegistry.flushDirtyIndexesAsync());

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
        fileStore.rememberStationSource(coordinates, stationSource);
    }

    public ItemSourceRef rememberedStationSource(StationCoordinates coordinates) {
        return fileStore.rememberedStationSource(coordinates);
    }

    public ItemSourceRef stationSource(YamlSection state) {
        return fileStore.stationSource(state);
    }

    public StorageInspection inspect(Block block) {
        if (block == null) {
            return StorageInspection.empty();
        }
        fileStore.requireLocationOwnership(block.getLocation());
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        YamlSection pdcState = fileStore.readPdcState(coordinates);
        YamlSection yamlState = fileStore.readYamlState(coordinates);
        YamlSection state = pdcState == null ? yamlState : pdcState;
        TileState tileState = fileStore.tileStateOf(block, false);
        StationIndexEntry entry = indexRegistry.entry(coordinates);
        StationType stateType = state == null ? null : fileStore.stationType(state);
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
        return indexRegistry.isIndexed(coordinates);
    }

    public StationType indexedStationType(StationCoordinates coordinates) {
        return indexRegistry.indexedStationType(coordinates);
    }

    public StationStorageBackend indexedBackend(StationCoordinates coordinates) {
        return indexRegistry.indexedBackend(coordinates);
    }

    public boolean hasLegacyYaml(StationCoordinates coordinates) {
        return fileStore.hasLegacyYaml(coordinates);
    }

    public StationStorageBackend backendFor(Block block) {
        return fileStore.backendFor(block);
    }

    public CompletableFuture<ReindexReport> reindexAsync() {
        return indexRegistry.reindexAsync();
    }

    public Set<StationCoordinates> indexedCoordinatesInChunk(World world, int chunkX, int chunkZ) {
        return indexRegistry.indexedCoordinatesInChunk(world, chunkX, chunkZ);
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
                        TaskToken handle = executionDispatcher.runAtLocation(plugin, location, task);
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
            if (!stationType.folderName().equalsIgnoreCase(state.getString(StationStateFileStore.STATION_TYPE_KEY, ""))) {
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
        if (fileStore.backendForCurrentBlock(entry.coordinates()) == StationStorageBackend.YAML_FALLBACK) {
            plugin.getLogger().warning("Station restore report: lost_block_entity_replaced type=" + entry.type().folderName()
                    + " coordinate=" + entry.coordinates().runtimeKey());
        }
    }

    private boolean isChunkLoaded(StationCoordinates coordinates) {
        if (coordinates == null || Texts.isBlank(coordinates.world())) {
            return false;
        }
        World world = Bukkit.getWorld(coordinates.world());
        return world != null && world.isChunkLoaded(coordinates.x() >> 4, coordinates.z() >> 4);
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
            TaskToken handle = executionDispatcher.runAtLocation(plugin, location, () -> {
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

    static String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof CompletionException ce && ce.getCause() != null) {
            cause = ce.getCause();
        }
        return cause == null ? "unknown" : String.valueOf(cause.getMessage());
    }

    static void debugStation(JavaPlugin plugin, String langKey, Map<String, ?> replacements) {
        DebugLogger debugLogger = plugin instanceof DebugLoggerProvider provider ? provider.debugLogger() : null;
        if (debugLogger == null) {
            return;
        }
        debugLogger.log("station", (UUID) null, langKey, replacements);
    }

    public enum StationStorageBackend {
        BLOCK_PDC,
        YAML_FALLBACK;

        static StationStorageBackend parse(String raw) {
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

    record StoredState(YamlSection state,
            long version,
            boolean tombstone,
            StationStorageBackend backend) {
    }

    record StationIndexEntry(StationCoordinates coordinates,
            StationType type,
            StationStorageBackend backend,
            ItemSourceRef source,
            long savedAtMs) {
    }
}
