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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.item.ItemSource;
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
    private static final int FORMAT_VERSION = 1;
    private static final long INDEX_FLUSH_DELAY_SECONDS = 2L;
    private static final String INDEX_EXTENSION = ".idx";

    private final JavaPlugin plugin;
    private final AsyncFileService asyncFileService;
    private final NamespacedKey stateKey;
    private final NamespacedKey stationTypeKey;
    private final NamespacedKey stationSourceKey;
    private final NamespacedKey formatVersionKey;
    private final NamespacedKey savedAtKey;
    private final ConcurrentMap<StationCoordinates, ItemSource> stationSources = new ConcurrentHashMap<>();
    private final ConcurrentMap<StationCoordinates, YamlSection> yamlCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<StationCoordinates, StationIndexEntry> index = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<Long, Set<StationCoordinates>>> chunkIndex = new ConcurrentHashMap<>();
    private final Set<String> dirtyIndexWorlds = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<?>> pendingOperations = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean indexLoaded = new AtomicBoolean(false);
    private final AtomicBoolean indexFlushScheduled = new AtomicBoolean(false);

    public StationStateStore(JavaPlugin plugin) {
        this(plugin, null);
    }

    public StationStateStore(JavaPlugin plugin, AsyncFileService asyncFileService) {
        this.plugin = plugin;
        this.asyncFileService = asyncFileService;
        this.stateKey = new NamespacedKey(plugin, "station_state");
        this.stationTypeKey = new NamespacedKey(plugin, "station_type");
        this.stationSourceKey = new NamespacedKey(plugin, "station_source");
        this.formatVersionKey = new NamespacedKey(plugin, "station_format_version");
        this.savedAtKey = new NamespacedKey(plugin, "station_saved_at_ms");
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
        long skippedUnloaded = typeEntries.stream()
                .filter(entry -> !isChunkLoaded(entry.coordinates()))
                .count();
        if (skippedUnloaded > 0L) {
            plugin.getLogger().info("Station restore report: type=" + stationType.folderName()
                    + " skipped_unloaded=" + skippedUnloaded);
        }
        Map<String, List<StationIndexEntry>> entriesByChunk = new LinkedHashMap<>();
        for (StationIndexEntry entry : typeEntries) {
            if (!isChunkLoaded(entry.coordinates())) {
                continue;
            }
            entriesByChunk.computeIfAbsent(chunkBucketKey(entry.coordinates()), _ -> new ArrayList<>()).add(entry);
        }
        if (entriesByChunk.isEmpty()) {
            return;
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (List<StationIndexEntry> entries : entriesByChunk.values()) {
            futures.add(runLoadedStateBatch(stationType, entries, consumer));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    public YamlSection load(StationCoordinates coordinates) {
        if (coordinates == null) {
            return null;
        }
        ensureIndexLoaded();
        YamlSection pdcState = readPdcState(coordinates);
        if (pdcState != null) {
            rememberStationSource(coordinates, stationSource(pdcState));
            recordIndex(coordinates, stationType(pdcState), StationStorageBackend.BLOCK_PDC, stationSource(pdcState), savedAt(pdcState), true);
            archiveYamlAsync(coordinates);
            return pdcState;
        }
        YamlSection yamlState = readYamlState(coordinates);
        if (yamlState == null) {
            StationIndexEntry existing = index.get(coordinates);
            if (existing != null && existing.backend() == StationStorageBackend.BLOCK_PDC && backendForCurrentBlock(coordinates) == StationStorageBackend.YAML_FALLBACK) {
                removeIndex(coordinates, true);
            }
            return null;
        }
        rememberStationSource(coordinates, stationSource(yamlState));
        StationType type = stationType(yamlState);
        if (tryMigrateYamlToPdc(coordinates, yamlState)) {
            recordIndex(coordinates, type, StationStorageBackend.BLOCK_PDC, stationSource(yamlState), savedAt(yamlState), true);
        } else {
            recordIndex(coordinates, type, StationStorageBackend.YAML_FALLBACK, stationSource(yamlState), savedAt(yamlState), true);
        }
        return yamlState;
    }

    public void save(StationCoordinates coordinates, Map<String, Object> state) {
        trySave(coordinates, state);
    }

    public boolean trySave(StationCoordinates coordinates, Map<String, Object> state) {
        if (coordinates == null || state == null || state.isEmpty()) {
            return false;
        }
        Map<String, Object> stateWithMetadata = stateWithMetadata(coordinates, state);
        YamlSection section = new MapYamlSection(stateWithMetadata);
        if (tryWritePdcState(coordinates, section)) {
            yamlCache.remove(coordinates);
            recordIndex(coordinates, stationType(section), StationStorageBackend.BLOCK_PDC, stationSource(section), savedAt(section), true);
            archiveYamlAsync(coordinates);
            return true;
        }
        return trySaveYamlFallback(coordinates, stateWithMetadata);
    }

    public void delete(StationCoordinates coordinates) {
        tryDelete(coordinates);
    }

    public boolean tryDelete(StationCoordinates coordinates) {
        if (coordinates == null) {
            return false;
        }
        stationSources.remove(coordinates);
        yamlCache.remove(coordinates);
        removePdcState(coordinates);
        removeIndex(coordinates, true);
        Path file = pathFor(coordinates);
        try {
            Files.deleteIfExists(file);
            cleanupParents(file.getParent());
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to delete station state " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return false;
        }
    }

    public CompletableFuture<Boolean> saveAsync(StationCoordinates coordinates, Map<String, Object> state) {
        if (coordinates == null || state == null || state.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, Object> stateWithMetadata = stateWithMetadata(coordinates, state);
        YamlSection section = new MapYamlSection(stateWithMetadata);
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        runOnBlockThread(coordinates, () -> {
            if (tryWritePdcState(coordinates, section)) {
                yamlCache.remove(coordinates);
                recordIndex(coordinates, stationType(section), StationStorageBackend.BLOCK_PDC, stationSource(section), savedAt(section), true);
                archiveYamlAsync(coordinates);
                future.complete(true);
                return;
            }
            saveYamlFallbackAsync(coordinates, stateWithMetadata)
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
        stationSources.remove(coordinates);
        yamlCache.remove(coordinates);
        removeIndex(coordinates, true);
        CompletableFuture<Boolean> pdcFuture = new CompletableFuture<>();
        runOnBlockThread(coordinates, () -> pdcFuture.complete(removePdcState(coordinates)), pdcFuture);
        CompletableFuture<Boolean> yamlFuture = deleteYamlFallbackAsync(coordinates);
        CompletableFuture<Boolean> result = CompletableFuture.allOf(pdcFuture, yamlFuture)
                .thenApply(_ -> Boolean.TRUE.equals(pdcFuture.getNow(false)) || Boolean.TRUE.equals(yamlFuture.getNow(false)))
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Async delete failed for station " + coordinates.runtimeKey() + ": " + rootCauseMessage(throwable));
                    return false;
                });
        return trackOperation(result);
    }

    public CompletableFuture<Void> waitForIdle() {
        CompletableFuture<?>[] operations = pendingOperations.toArray(CompletableFuture[]::new);
        CompletableFuture<Void> pending = operations.length == 0
                ? CompletableFuture.completedFuture(null)
                : CompletableFuture.allOf(operations);
        return pending.exceptionally(throwable -> null)
                .thenCompose(_ -> flushDirtyIndexesAsync())
                .thenCompose(_ -> asyncFileService == null
                        ? CompletableFuture.completedFuture(null)
                        : asyncFileService.waitForIdle());
    }

    public void rememberStationSource(StationCoordinates coordinates, ItemSource stationSource) {
        if (coordinates == null || stationSource == null) {
            return;
        }
        String shorthand = ItemSourceUtil.toShorthand(stationSource);
        if (shorthand == null || shorthand.isBlank()) {
            return;
        }
        stationSources.put(coordinates, stationSource);
    }

    public ItemSource rememberedStationSource(StationCoordinates coordinates) {
        return coordinates == null ? null : stationSources.get(coordinates);
    }

    public ItemSource stationSource(YamlSection state) {
        if (state == null) {
            return null;
        }
        return ItemSourceUtil.parse(state.getString(STATION_SOURCE_KEY, ""));
    }

    public StorageInspection inspect(Block block) {
        if (block == null) {
            return StorageInspection.empty();
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(block);
        YamlSection pdcState = readPdcState(coordinates);
        YamlSection yamlState = readYamlState(coordinates);
        YamlSection state = pdcState == null ? yamlState : pdcState;
        TileState tileState = tileStateOf(block, false);
        StationIndexEntry entry = index.get(coordinates);
        StationType stateType = state == null ? null : stationType(state);
        ItemSource source = state == null ? null : stationSource(state);
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
        Location location = entries == null || entries.isEmpty() ? null : chunkCenterLocation(entries.getFirst().coordinates());
        if (location == null || !FoliaSchedulerAdapter.isFolia()) {
            task.run();
            return future;
        }
        try {
            if (FoliaSchedulerAdapter.runAtLocation(plugin, location, task) == null) {
                task.run();
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
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
        future.whenComplete((_, _) -> pendingOperations.remove(future));
        return future;
    }

    private CompletableFuture<Boolean> saveYamlFallbackAsync(StationCoordinates coordinates, Map<String, Object> state) {
        YamlSection section = new MapYamlSection(state);
        StationType type = stationType(section);
        ItemSource source = stationSource(section);
        long savedAt = savedAt(section);
        if (asyncFileService == null) {
            return CompletableFuture.completedFuture(trySaveYamlFallback(coordinates, state));
        }
        Path path = pathFor(coordinates);
        return asyncFileService.write(path, "station-yaml-save:" + coordinates.runtimeKey(), () -> {
            try {
                YamlFiles.save(path.toFile(), state);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).thenApply(_ -> {
            yamlCache.put(coordinates, section.copy());
            recordIndex(coordinates, type, StationStorageBackend.YAML_FALLBACK, source, savedAt, true);
            return true;
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("YAML fallback save failed for station " + coordinates.runtimeKey() + ": " + rootCauseMessage(throwable));
            return false;
        });
    }

    private boolean trySaveYamlFallback(StationCoordinates coordinates, Map<String, Object> state) {
        YamlSection section = new MapYamlSection(state);
        try {
            YamlFiles.save(pathFor(coordinates).toFile(), state);
            yamlCache.put(coordinates, section.copy());
            recordIndex(coordinates, stationType(section), StationStorageBackend.YAML_FALLBACK, stationSource(section), savedAt(section), true);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save YAML fallback state " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return false;
        }
    }

    private CompletableFuture<Boolean> deleteYamlFallbackAsync(StationCoordinates coordinates) {
        if (asyncFileService == null) {
            Path path = pathFor(coordinates);
            try {
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
        return asyncFileService.write(path, "station-yaml-delete:" + coordinates.runtimeKey(), () -> {
            try {
                Files.deleteIfExists(path);
                cleanupParents(path.getParent());
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).thenApply(_ -> true).exceptionally(throwable -> {
            plugin.getLogger().warning("YAML fallback delete failed for station " + coordinates.runtimeKey() + ": " + rootCauseMessage(throwable));
            return false;
        });
    }

    private YamlSection readPdcState(StationCoordinates coordinates) {
        if (coordinates == null) {
            return null;
        }
        TileState tileState = tileStateOf(coordinates.block(), false);
        if (tileState == null) {
            return null;
        }
        String payload = tileState.getPersistentDataContainer().get(stateKey, PersistentDataType.STRING);
        if (Texts.isBlank(payload)) {
            return null;
        }
        try {
            YamlSection section = YamlFiles.load(payload);
            if (section == null || section.isEmpty()) {
                return null;
            }
            return normalizeLoadedState(coordinates, section);
        } catch (YamlLoadException exception) {
            plugin.getLogger().warning("Failed to decode PDC station state " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return null;
        }
    }

    private YamlSection readYamlState(StationCoordinates coordinates) {
        if (coordinates == null) {
            return null;
        }
        YamlSection cached = yamlCache.get(coordinates);
        if (cached != null) {
            return cached.copy();
        }
        Path file = pathFor(coordinates);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            YamlSection state = normalizeLoadedState(coordinates, YamlFiles.load(file.toFile()));
            yamlCache.put(coordinates, state.copy());
            return state;
        } catch (YamlLoadException exception) {
            if (!causedByMissingFile(exception)) {
                throw exception;
            }
            return null;
        }
    }

    private boolean tryWritePdcState(StationCoordinates coordinates, YamlSection state) {
        if (coordinates == null || state == null || state.isEmpty()) {
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
        try {
            if (!tileState.update(false, false)) {
                return false;
            }
            return true;
        } catch (Exception exception) {
            plugin.getLogger().warning("PDC station save failed for " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return false;
        }
    }

    private boolean removePdcState(StationCoordinates coordinates) {
        if (coordinates == null) {
            return false;
        }
        TileState tileState = tileStateOf(coordinates.block(), true);
        if (tileState == null) {
            return false;
        }
        PersistentDataContainer container = tileState.getPersistentDataContainer();
        boolean present = container.has(stateKey, PersistentDataType.STRING)
                || container.has(stationTypeKey, PersistentDataType.STRING)
                || container.has(stationSourceKey, PersistentDataType.STRING);
        container.remove(stateKey);
        container.remove(stationTypeKey);
        container.remove(stationSourceKey);
        container.remove(formatVersionKey);
        container.remove(savedAtKey);
        try {
            tileState.update(false, false);
            return present;
        } catch (Exception exception) {
            plugin.getLogger().warning("PDC station delete failed for " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return false;
        }
    }

    private boolean tryMigrateYamlToPdc(StationCoordinates coordinates, YamlSection yamlState) {
        if (coordinates == null || yamlState == null || yamlState.isEmpty()) {
            return false;
        }
        if (tryWritePdcState(coordinates, yamlState)) {
            archiveYamlAsync(coordinates);
            yamlCache.remove(coordinates);
            return true;
        }
        return false;
    }

    private void archiveYamlAsync(StationCoordinates coordinates) {
        if (coordinates == null) {
            return;
        }
        Path source = pathFor(coordinates);
        if (!Files.exists(source)) {
            return;
        }
        Path target = legacyBackupPathFor(coordinates);
        Runnable task = () -> {
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
        if (asyncFileService == null) {
            try {
                task.run();
            } catch (CompletionException exception) {
                plugin.getLogger().warning("Failed to archive legacy station YAML " + coordinates.runtimeKey() + ": " + rootCauseMessage(exception));
            }
            return;
        }
        asyncFileService.write(source, "station-yaml-archive:" + coordinates.runtimeKey(), task)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to archive legacy station YAML " + coordinates.runtimeKey() + ": " + rootCauseMessage(throwable));
                    return null;
                });
    }

    private Map<String, Object> stateWithMetadata(StationCoordinates coordinates, Map<String, Object> state) {
        Map<String, Object> copy = new LinkedHashMap<>(MapYamlSection.normalizeMap(state));
        copy.putIfAbsent("world", coordinates.world());
        copy.putIfAbsent("x", coordinates.x());
        copy.putIfAbsent("y", coordinates.y());
        copy.putIfAbsent("z", coordinates.z());
        copy.put(SAVED_AT_KEY, System.currentTimeMillis());
        copy.put(FORMAT_VERSION_KEY, FORMAT_VERSION);
        ItemSource explicitSource = ItemSourceUtil.parse(copy.get(STATION_SOURCE_KEY));
        if (explicitSource != null) {
            rememberStationSource(coordinates, explicitSource);
            copy.put(STATION_SOURCE_KEY, ItemSourceUtil.toShorthand(explicitSource));
            return copy;
        }
        ItemSource remembered = rememberedStationSource(coordinates);
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
        if (asyncFileService == null) {
            return CompletableFuture.completedFuture(scanLegacyYamlSync());
        }
        return asyncFileService.read("station-index-scan-legacy-yaml", this::scanLegacyYamlSync)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Legacy station YAML scan failed: " + rootCauseMessage(throwable));
                    return 0;
                });
    }

    private CompletableFuture<Integer> scanLoadedPdcStationsAsync() {
        CompletableFuture<List<Chunk>> snapshotFuture = new CompletableFuture<>();
        Runnable snapshotTask = () -> {
            try {
                List<Chunk> chunks = new ArrayList<>();
                for (World world : Bukkit.getWorlds()) {
                    chunks.addAll(List.of(world.getLoadedChunks()));
                }
                snapshotFuture.complete(chunks);
            } catch (Throwable throwable) {
                snapshotFuture.completeExceptionally(throwable);
            }
        };
        try {
            if (FoliaSchedulerAdapter.runTask(plugin, snapshotTask) == null) {
                snapshotTask.run();
            }
        } catch (Throwable throwable) {
            snapshotFuture.completeExceptionally(throwable);
        }
        return snapshotFuture.thenCompose(chunks -> {
            if (chunks.isEmpty()) {
                return CompletableFuture.completedFuture(0);
            }
            List<CompletableFuture<Integer>> futures = new ArrayList<>();
            for (Chunk chunk : chunks) {
                futures.add(scanLoadedPdcChunkAsync(chunk));
            }
            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .thenApply(_ -> futures.stream().mapToInt(future -> future.getNow(0)).sum());
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Loaded TileState PDC reindex failed: " + rootCauseMessage(throwable));
            return 0;
        });
    }

    private CompletableFuture<Integer> scanLoadedPdcChunkAsync(Chunk chunk) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        if (chunk == null || chunk.getWorld() == null || !chunk.isLoaded()) {
            future.complete(0);
            return future;
        }
        Runnable task = () -> {
            try {
                future.complete(scanLoadedPdcChunkSync(chunk));
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        };
        Location location = new Location(chunk.getWorld(), (chunk.getX() << 4) + 8D, 0D, (chunk.getZ() << 4) + 8D);
        try {
            if (FoliaSchedulerAdapter.runAtLocation(plugin, location, task) == null) {
                task.run();
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
            ItemSource source,
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
            FoliaSchedulerAdapter.runAsyncLater(plugin, () -> {
                indexFlushScheduled.set(false);
                flushDirtyIndexesAsync();
            }, INDEX_FLUSH_DELAY_SECONDS, TimeUnit.SECONDS);
        } catch (Throwable ignored) {
            indexFlushScheduled.set(false);
            flushDirtyIndexesAsync();
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
        if (asyncFileService == null) {
            try {
                task.run();
                return CompletableFuture.completedFuture(null);
            } catch (CompletionException exception) {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(exception);
                return failed;
            }
        }
        return asyncFileService.write(file, "station-index-save:" + world, task)
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
            ItemSource source = parts.length >= 7 ? ItemSourceUtil.parse(parts[6]) : null;
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
        if (location == null || !FoliaSchedulerAdapter.isFolia()) {
            try {
                task.run();
            } catch (Throwable throwable) {
                if (future != null) {
                    future.completeExceptionally(throwable);
                }
            }
            return;
        }
        try {
            if (FoliaSchedulerAdapter.runAtLocation(plugin, location, () -> {
                try {
                    task.run();
                } catch (Throwable throwable) {
                    if (future != null) {
                        future.completeExceptionally(throwable);
                    }
                }
            }) == null) {
                task.run();
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

    private record StationIndexEntry(StationCoordinates coordinates,
            StationType type,
            StationStorageBackend backend,
            ItemSource source,
            long savedAtMs) {
    }
}
