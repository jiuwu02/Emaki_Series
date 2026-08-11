package emaki.jiuwu.craft.cooking.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.async.AsyncFileService.FileScope;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.StationStateStore.StationIndexEntry;
import emaki.jiuwu.craft.cooking.service.StationStateStore.StationStorageBackend;
import emaki.jiuwu.craft.cooking.service.StationStateStore.StoredState;




final class StationIndexRegistry {

    private static final long INDEX_FLUSH_DELAY_SECONDS = 2L;
    private static final String INDEX_EXTENSION = ".idx";

    private final JavaPlugin plugin;
    private final FileScope fileScope;
    private final ExecutionDispatcher executionDispatcher;
    private final StationStateFileStore fileStore;
    private final StationStateArbiter arbiter;
    private final Consumer<CompletableFuture<?>> operationTracker;
    private final ConcurrentMap<StationCoordinates, StationIndexEntry> entries = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConcurrentMap<Long, Set<StationCoordinates>>> chunkIndex = new ConcurrentHashMap<>();
    private final Set<String> dirtyIndexWorlds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean indexLoaded = new AtomicBoolean(false);
    private final AtomicBoolean indexFlushScheduled = new AtomicBoolean(false);
    private volatile TaskHandle indexFlushTask;

    StationIndexRegistry(JavaPlugin plugin,
            FileScope fileScope,
            ExecutionDispatcher executionDispatcher,
            StationStateFileStore fileStore,
            StationStateArbiter arbiter,
            Consumer<CompletableFuture<?>> operationTracker) {
        this.plugin = plugin;
        this.fileScope = fileScope;
        this.executionDispatcher = executionDispatcher;
        this.fileStore = fileStore;
        this.arbiter = arbiter;
        this.operationTracker = operationTracker;
    }

    void ensureIndexLoaded() {
        if (indexLoaded.get()) {
            return;
        }
        synchronized (indexLoaded) {
            if (indexLoaded.get()) {
                return;
            }
            boolean loaded = loadIndexFiles();
            if (!loaded || entries.isEmpty()) {
                int legacyCount = scanLegacyYamlSync();
                if (legacyCount > 0) {
                    flushDirtyIndexesAsync();
                }
            }
            indexLoaded.set(true);
        }
    }

    int size() {
        return entries.size();
    }

    StationIndexEntry entry(StationCoordinates coordinates) {
        return entries.get(coordinates);
    }

    boolean isIndexed(StationCoordinates coordinates) {
        ensureIndexLoaded();
        return coordinates != null && entries.containsKey(coordinates);
    }

    StationType indexedStationType(StationCoordinates coordinates) {
        ensureIndexLoaded();
        StationIndexEntry entry = coordinates == null ? null : entries.get(coordinates);
        return entry == null ? null : entry.type();
    }

    StationStorageBackend indexedBackend(StationCoordinates coordinates) {
        ensureIndexLoaded();
        StationIndexEntry entry = coordinates == null ? null : entries.get(coordinates);
        return entry == null ? null : entry.backend();
    }

    Set<StationCoordinates> indexedCoordinatesInChunk(World world, int chunkX, int chunkZ) {
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

    Map<String, List<StationIndexEntry>> entriesByChunkForType(StationType stationType) {
        ensureIndexLoaded();
        List<StationIndexEntry> typeEntries = entries.values().stream()
                .filter(entry -> entry != null && entry.type() == stationType)
                .sorted(Comparator.comparing(entry -> entry.coordinates().runtimeKey()))
                .toList();
        Map<String, List<StationIndexEntry>> entriesByChunk = new LinkedHashMap<>();
        for (StationIndexEntry entry : typeEntries) {
            entriesByChunk.computeIfAbsent(chunkBucketKey(entry.coordinates()), _ -> new ArrayList<>()).add(entry);
        }
        return entriesByChunk;
    }

    void recordIndex(StationCoordinates coordinates,
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
            fileStore.rememberStationSource(coordinates, source);
        }
    }

    void removeIndex(StationCoordinates coordinates, boolean dirty) {
        if (coordinates == null) {
            return;
        }
        StationIndexEntry removed = entries.remove(coordinates);
        if (removed != null) {
            removeChunkIndex(coordinates);
        }
        if (dirty && removed != null) {
            scheduleIndexFlush(coordinates.world());
        }
    }

    private void putIndexEntry(StationIndexEntry entry, boolean dirty) {
        if (entry == null || entry.coordinates() == null) {
            return;
        }
        StationIndexEntry previous = entries.put(entry.coordinates(), entry);
        if (previous != null) {
            removeChunkIndex(previous.coordinates());
        }
        addChunkIndex(entry.coordinates());
        if (dirty) {
            scheduleIndexFlush(entry.coordinates().world());
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

    private Set<String> resetIndexForRebuild() {
        Set<String> affectedWorlds = new LinkedHashSet<>(chunkIndex.keySet());
        for (StationCoordinates coordinates : entries.keySet()) {
            if (coordinates != null && Texts.isNotBlank(coordinates.world())) {
                affectedWorlds.add(coordinates.world());
            }
        }
        entries.clear();
        chunkIndex.clear();
        return affectedWorlds;
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
                operationTracker.accept(flushDirtyIndexesAsync());
            }, INDEX_FLUSH_DELAY_SECONDS * 20L);
        } catch (Throwable throwable) {
            indexFlushTask = null;
            indexFlushScheduled.set(false);
            operationTracker.accept(flushDirtyIndexesAsync());
            plugin.getLogger().warning("Failed to schedule station index flush: " + StationStateStore.rootCauseMessage(throwable));
        }
    }

    void cancelIndexFlushTask() {
        TaskHandle task = indexFlushTask;
        indexFlushTask = null;
        indexFlushScheduled.set(false);
        if (task != null) {
            task.cancel();
        }
    }

    CompletableFuture<Void> flushDirtyIndexesAsync() {
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
        List<String> lines = entries.values().stream()
                .filter(entry -> entry != null && world.equals(entry.coordinates().world()))
                .sorted(Comparator.comparing(entry -> entry.coordinates().runtimeKey()))
                .map(this::formatIndexLine)
                .toList();
        Runnable task = () -> {
            try {
                if (lines.isEmpty()) {
                    Files.deleteIfExists(file);
                    fileStore.cleanupParents(file.getParent());
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
                    plugin.getLogger().warning("Failed to write station index for world " + world + ": " + StationStateStore.rootCauseMessage(throwable));
                    return null;
                });
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
                        fileStore.rememberStationSource(entry.coordinates(), entry.source());
                    }
                }
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to load station index: " + exception.getMessage());
        }
        return loadedAny;
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
            StationType type = fileStore.resolveStationType(parts[1]);
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

    private Path indexRoot() {
        return fileStore.stationsRoot().resolve("index");
    }

    private Path indexFileFor(String world) {
        return indexRoot().resolve(fileStore.sanitizeWorld(world) + INDEX_EXTENSION);
    }

    CompletableFuture<StationStateStore.ReindexReport> reindexAsync() {
        ensureIndexLoaded();
        dirtyIndexWorlds.addAll(resetIndexForRebuild());
        return scanLegacyYamlAsync().thenCompose(legacyCount -> scanLoadedPdcStationsAsync()
                .thenCompose(pdcCount -> flushDirtyIndexesAsync()
                        .thenApply(_ -> new StationStateStore.ReindexReport(legacyCount, pdcCount, entries.size()))));
    }

    private int scanLegacyYamlSync() {
        Path root = fileStore.stationsRoot();
        if (!Files.exists(root)) {
            return 0;
        }
        int count = 0;
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(fileStore::isYamlFile)
                    .filter(path -> !path.startsWith(indexRoot()))
                    .toList()) {
                YamlSection state = YamlFiles.load(file.toFile());
                StationType type = fileStore.stationType(state);
                StationCoordinates coordinates = StationCoordinates.fromSection(state);
                if (type == null || coordinates == null) {
                    continue;
                }
                YamlSection normalized = fileStore.normalizeLoadedState(coordinates, state);
                StoredState selected = arbiter.latestState(
                        normalized == null ? null : new StoredState(
                                normalized,
                                fileStore.stateVersion(normalized),
                                fileStore.tombstone(normalized),
                                StationStorageBackend.YAML_FALLBACK),
                        fileStore.readTombstoneCandidate(coordinates)
                );
                if (selected == null || selected.tombstone() || selected.state() == null) {
                    continue;
                }
                normalized = selected.state();
                fileStore.cacheYamlState(coordinates, normalized);
                fileStore.rememberStationSource(coordinates, fileStore.stationSource(normalized));
                recordIndex(coordinates, type, StationStorageBackend.YAML_FALLBACK, fileStore.stationSource(normalized), fileStore.savedAt(normalized), true);
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
                    plugin.getLogger().warning("Legacy station YAML scan failed: " + StationStateStore.rootCauseMessage(throwable));
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
            plugin.getLogger().warning("Loaded TileState PDC reindex failed: " + StationStateStore.rootCauseMessage(throwable));
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
            if (!fileStore.hasPdcStatePayload(tileState)) {
                continue;
            }
            StationCoordinates coordinates = new StationCoordinates(world.getName(), state.getX(), state.getY(), state.getZ());
            YamlSection section = fileStore.readPdcState(coordinates);
            StationType type = fileStore.stationType(section);
            if (type == null) {
                continue;
            }
            recordIndex(coordinates, type, StationStorageBackend.BLOCK_PDC, fileStore.stationSource(section), fileStore.savedAt(section), true);
            count++;
        }
        return count;
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

    private record LoadedChunkRef(String worldName, int chunkX, int chunkZ) {
    }
}
