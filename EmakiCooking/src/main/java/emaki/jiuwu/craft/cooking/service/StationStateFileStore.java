package emaki.jiuwu.craft.cooking.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.async.AsyncFileService.FileScope;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlLoadException;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.StationStateStore.StationStorageBackend;
import emaki.jiuwu.craft.cooking.service.StationStateStore.StoredState;




final class StationStateFileStore {

    static final String STATION_SOURCE_KEY = "station_source";
    static final String STATION_TYPE_KEY = "station_type";
    static final String SAVED_AT_KEY = "station_saved_at_ms";
    static final String FORMAT_VERSION_KEY = "station_format_version";
    static final String STATE_VERSION_KEY = "station_state_version";
    static final String TOMBSTONE_KEY = "station_tombstone";
    static final int FORMAT_VERSION = 1;

    private final JavaPlugin plugin;
    private final FileScope fileScope;
    private final ThreadOwnership threadOwnership;
    private final StationStateArbiter arbiter;
    private final NamespacedKey stateKey;
    private final NamespacedKey stationTypeKey;
    private final NamespacedKey stationSourceKey;
    private final NamespacedKey formatVersionKey;
    private final NamespacedKey savedAtKey;
    private final NamespacedKey stateVersionKey;
    private final NamespacedKey tombstoneKey;
    private final ConcurrentMap<StationCoordinates, ItemSourceRef> stationSources = new ConcurrentHashMap<>();
    private final ConcurrentMap<StationCoordinates, YamlSection> yamlCache = new ConcurrentHashMap<>();
    private volatile StationIndexRegistry indexRegistry;

    StationStateFileStore(JavaPlugin plugin,
            FileScope fileScope,
            ThreadOwnership threadOwnership,
            StationStateArbiter arbiter) {
        this.plugin = plugin;
        this.fileScope = fileScope;
        this.threadOwnership = threadOwnership;
        this.arbiter = arbiter;
        this.stateKey = new NamespacedKey(plugin, "station_state");
        this.stationTypeKey = new NamespacedKey(plugin, "station_type");
        this.stationSourceKey = new NamespacedKey(plugin, "station_source");
        this.formatVersionKey = new NamespacedKey(plugin, "station_format_version");
        this.savedAtKey = new NamespacedKey(plugin, "station_saved_at_ms");
        this.stateVersionKey = new NamespacedKey(plugin, "station_state_version");
        this.tombstoneKey = new NamespacedKey(plugin, "station_tombstone");
    }

    void attachIndexRegistry(StationIndexRegistry indexRegistry) {
        this.indexRegistry = indexRegistry;
    }

    FileScope fileScope() {
        return fileScope;
    }

    YamlSection readPdcState(StationCoordinates coordinates) {
        StoredState candidate = arbiter.latestState(readPdcCandidate(coordinates), readTombstoneCandidate(coordinates));
        return candidate == null || candidate.tombstone() ? null : candidate.state();
    }

    StoredState readPdcCandidate(StationCoordinates coordinates) {
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

    YamlSection readYamlState(StationCoordinates coordinates) {
        StoredState candidate = arbiter.latestState(readYamlCandidate(coordinates), readTombstoneCandidate(coordinates));
        return candidate == null || candidate.tombstone() ? null : candidate.state();
    }

    StoredState readYamlCandidate(StationCoordinates coordinates) {
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

    StoredState readTombstoneCandidate(StationCoordinates coordinates) {
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

    boolean tryWritePdcState(StationCoordinates coordinates, YamlSection state, long mutationVersion) {
        if (coordinates == null || state == null || state.isEmpty() || !arbiter.isCurrentSave(coordinates, mutationVersion)) {
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
            return arbiter.isCurrentSave(coordinates, mutationVersion);
        } catch (Exception exception) {
            plugin.getLogger().warning("PDC station save failed for " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return false;
        }
    }

    boolean removePdcState(StationCoordinates coordinates, long mutationVersion) {
        if (coordinates == null || !arbiter.isCurrentDelete(coordinates, mutationVersion)) {
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
            return arbiter.isCurrentDelete(coordinates, mutationVersion);
        } catch (Exception exception) {
            plugin.getLogger().warning("PDC station delete failed for " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return false;
        }
    }

    boolean tryMigrateYamlToPdc(StationCoordinates coordinates, YamlSection yamlState) {
        if (coordinates == null || yamlState == null || yamlState.isEmpty() || arbiter.isTombstoned(coordinates)) {
            return false;
        }
        if (backendForCurrentBlock(coordinates) == StationStorageBackend.YAML_FALLBACK) {
            StationStateStore.debugStation(plugin, "station.migrate_skipped", Map.of(
                    "station", coordinates.runtimeKey(),
                    "reason", "block_is_not_tile_state"
            ));
            return false;
        }
        StationStateVersionLedger.Mutation previous = arbiter.currentMutation(coordinates);
        long mutationVersion = arbiter.beginSave(coordinates);
        Map<String, Object> stateWithVersion = new LinkedHashMap<>(yamlState.asMap());
        stateWithVersion.put(STATE_VERSION_KEY, mutationVersion);
        stateWithVersion.put(TOMBSTONE_KEY, false);
        YamlSection versionedState = new MapYamlSection(stateWithVersion);
        if (tryWritePdcState(coordinates, versionedState, mutationVersion)) {
            archiveYamlAsync(coordinates, mutationVersion);
            yamlCache.remove(coordinates);
            StationStateStore.debugStation(plugin, "station.migrate_ok", Map.of(
                    "station", coordinates.runtimeKey(),
                    "version", mutationVersion
            ));
            return true;
        }
        boolean rolledBack = arbiter.abandonMutation(coordinates, mutationVersion, previous);
        StationStateStore.debugStation(plugin, "station.migrate_failed", Map.of(
                "station", coordinates.runtimeKey(),
                "version", mutationVersion,
                "previous_version", previous == null ? "none" : previous.version(),
                "rolled_back", rolledBack
        ));
        return false;
    }

    CompletableFuture<Boolean> saveYamlFallbackAsync(StationCoordinates coordinates, Map<String, Object> state, long mutationVersion) {
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
            if (!arbiter.isCurrentSave(coordinates, mutationVersion)) {
                return;
            }
            try {
                YamlFiles.save(path.toFile(), state);
                wrote.set(true);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).thenApply(_ -> {
            if (!wrote.get() || !arbiter.isCurrentSave(coordinates, mutationVersion)) {
                return false;
            }
            yamlCache.put(coordinates, section.copy());
            recordIndex(coordinates, type, StationStorageBackend.YAML_FALLBACK, source, savedAt, true);
            return true;
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("YAML fallback save failed for station " + coordinates.runtimeKey() + ": " + StationStateStore.rootCauseMessage(throwable));
            return false;
        });
    }

    private boolean trySaveYamlFallback(StationCoordinates coordinates, Map<String, Object> state, long mutationVersion) {
        if (!arbiter.isCurrentSave(coordinates, mutationVersion)) {
            return false;
        }
        YamlSection section = new MapYamlSection(state);
        try {
            YamlFiles.save(pathFor(coordinates).toFile(), state);
            if (!arbiter.isCurrentSave(coordinates, mutationVersion)) {
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

    CompletableFuture<Boolean> writeTombstoneAsync(StationCoordinates coordinates, long mutationVersion) {
        if (coordinates == null || !arbiter.isCurrentDelete(coordinates, mutationVersion)) {
            return CompletableFuture.completedFuture(false);
        }
        Map<String, Object> tombstone = stateWithMetadata(coordinates, Map.of(), mutationVersion, true);
        Path path = tombstonePathFor(coordinates);
        if (fileScope == null) {
            return CompletableFuture.completedFuture(tryWriteTombstone(path, coordinates, mutationVersion, tombstone));
        }
        AtomicBoolean written = new AtomicBoolean(false);
        return fileScope.write(path, "station-tombstone-save:" + coordinates.runtimeKey(), () -> {
            if (!arbiter.isCurrentDelete(coordinates, mutationVersion)) {
                return;
            }
            try {
                YamlFiles.save(path.toFile(), tombstone);
                written.set(true);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).thenApply(_ -> written.get() && arbiter.isCurrentDelete(coordinates, mutationVersion));
    }

    private boolean tryWriteTombstone(Path path,
            StationCoordinates coordinates,
            long mutationVersion,
            Map<String, Object> tombstone) {
        if (!arbiter.isCurrentDelete(coordinates, mutationVersion)) {
            return false;
        }
        try {
            YamlFiles.save(path.toFile(), tombstone);
            return arbiter.isCurrentDelete(coordinates, mutationVersion);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to persist station tombstone " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return false;
        }
    }

    CompletableFuture<Boolean> deleteYamlFallbackAsync(StationCoordinates coordinates, long mutationVersion) {
        if (fileScope == null) {
            Path path = pathFor(coordinates);
            try {
                if (!arbiter.isCurrentDelete(coordinates, mutationVersion)) {
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
            if (!arbiter.isCurrentDelete(coordinates, mutationVersion)) {
                return;
            }
            try {
                Files.deleteIfExists(path);
                cleanupParents(path.getParent());
                deleted.set(true);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).thenApply(_ -> deleted.get() && arbiter.isCurrentDelete(coordinates, mutationVersion)).exceptionally(throwable -> {
            plugin.getLogger().warning("YAML fallback delete failed for station " + coordinates.runtimeKey() + ": " + StationStateStore.rootCauseMessage(throwable));
            return false;
        });
    }

    void archiveYamlIfUnchangedAsync(StationCoordinates coordinates) {
        if (coordinates == null || arbiter.isTombstoned(coordinates)) {
            return;
        }
        archiveYamlAsync(coordinates, arbiter.currentVersion(coordinates), false);
    }

    void archiveYamlAsync(StationCoordinates coordinates, long mutationVersion) {
        archiveYamlAsync(coordinates, mutationVersion, true);
    }

    private void archiveYamlAsync(StationCoordinates coordinates, long mutationVersion, boolean requireCurrentSave) {
        if (coordinates == null || !arbiter.canArchiveYaml(coordinates, mutationVersion, requireCurrentSave)) {
            return;
        }
        Path source = pathFor(coordinates);
        if (!Files.exists(source)) {
            return;
        }
        Path target = legacyBackupPathFor(coordinates);
        Runnable task = () -> {
            if (!arbiter.canArchiveYaml(coordinates, mutationVersion, requireCurrentSave)) {
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
                plugin.getLogger().warning("Failed to archive legacy station YAML " + coordinates.runtimeKey() + ": " + StationStateStore.rootCauseMessage(exception));
            }
            return;
        }
        fileScope.write(source, "station-yaml-archive:" + coordinates.runtimeKey(), task)
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Failed to archive legacy station YAML " + coordinates.runtimeKey() + ": " + StationStateStore.rootCauseMessage(throwable));
                    return null;
                });
    }

    Map<String, Object> stateWithMetadata(StationCoordinates coordinates, Map<String, Object> state, long mutationVersion, boolean tombstone) {
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

    YamlSection normalizeLoadedState(StationCoordinates coordinates, YamlSection state) {
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

    StationStorageBackend backendForCurrentBlock(StationCoordinates coordinates) {
        return tileStateOf(coordinates == null ? null : coordinates.block(), false) == null
                ? StationStorageBackend.YAML_FALLBACK
                : StationStorageBackend.BLOCK_PDC;
    }

    StationStorageBackend backendFor(Block block) {
        if (block == null) {
            return StationStorageBackend.YAML_FALLBACK;
        }
        requireLocationOwnership(block.getLocation());
        return tileStateOf(block, false) == null ? StationStorageBackend.YAML_FALLBACK : StationStorageBackend.BLOCK_PDC;
    }

    TileState tileStateOf(Block block, boolean writable) {
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

    boolean hasPdcStatePayload(TileState tileState) {
        if (tileState == null) {
            return false;
        }
        return Texts.isNotBlank(tileState.getPersistentDataContainer().get(stateKey, PersistentDataType.STRING));
    }

    StationType stationType(YamlSection state) {
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

    StationType resolveStationType(String raw) {
        String normalized = Texts.normalizeId(raw);
        for (StationType type : StationType.values()) {
            if (normalized.equals(Texts.normalizeId(type.folderName())) || normalized.equals(Texts.normalizeId(type.name()))) {
                return type;
            }
        }
        return null;
    }

    ItemSourceRef stationSource(YamlSection state) {
        if (state == null) {
            return null;
        }
        return ItemSourceUtil.parse(state.getString(STATION_SOURCE_KEY, ""));
    }

    long savedAt(YamlSection state) {
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

    long stateVersion(YamlSection state) {
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

    boolean tombstone(YamlSection state) {
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

    void rememberStationSource(StationCoordinates coordinates, ItemSourceRef stationSource) {
        if (coordinates == null || stationSource == null) {
            return;
        }
        String shorthand = ItemSourceUtil.toShorthand(stationSource);
        if (shorthand == null || shorthand.isBlank()) {
            return;
        }
        stationSources.put(coordinates, stationSource);
    }

    ItemSourceRef rememberedStationSource(StationCoordinates coordinates) {
        return coordinates == null ? null : stationSources.get(coordinates);
    }

    void forgetStationSource(StationCoordinates coordinates) {
        stationSources.remove(coordinates);
    }

    void cacheYamlState(StationCoordinates coordinates, YamlSection state) {
        yamlCache.put(coordinates, state.copy());
    }

    void invalidateYamlCache(StationCoordinates coordinates) {
        yamlCache.remove(coordinates);
    }

    boolean hasLegacyYaml(StationCoordinates coordinates) {
        return coordinates != null && Files.exists(pathFor(coordinates));
    }

    Path pathFor(StationCoordinates coordinates) {
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

    Path stationsRoot() {
        return plugin.getDataFolder().toPath().resolve("data").resolve("stations");
    }

    String sanitizeWorld(String world) {
        String normalized = Texts.toStringSafe(world).trim().replaceAll("[^a-zA-Z0-9._-]+", "_");
        return normalized.isBlank() ? "world" : normalized;
    }

    boolean isYamlFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    void cleanupParents(Path directory) throws IOException {
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

    void requireLocationOwnership(Location location) {
        if (location == null || threadOwnership == null || threadOwnership.isLocationOwned(location)) {
            return;
        }
        throw new IllegalStateException("StationStateStore Bukkit state access requires location ownership");
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

    private void recordIndex(StationCoordinates coordinates,
            StationType type,
            StationStorageBackend backend,
            ItemSourceRef source,
            long savedAt,
            boolean dirty) {
        StationIndexRegistry registry = indexRegistry;
        if (registry == null) {
            return;
        }
        registry.recordIndex(coordinates, type, backend, source, savedAt, dirty);
    }
}
