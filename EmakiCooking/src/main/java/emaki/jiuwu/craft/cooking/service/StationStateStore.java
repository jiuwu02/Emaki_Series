package emaki.jiuwu.craft.cooking.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlLoadException;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class StationStateStore {

    private static final String STATION_SOURCE_KEY = "station_source";

    private final JavaPlugin plugin;
    private final AsyncFileService asyncFileService;
    private final Map<StationCoordinates, ItemSource> stationSources = new ConcurrentHashMap<>();

    public StationStateStore(JavaPlugin plugin) {
        this(plugin, null);
    }

    public StationStateStore(JavaPlugin plugin, AsyncFileService asyncFileService) {
        this.plugin = plugin;
        this.asyncFileService = asyncFileService;
    }


    public Map<StationCoordinates, YamlSection> loadAll(StationType stationType) {
        Map<StationCoordinates, YamlSection> states = new LinkedHashMap<>();
        Path root = plugin.getDataFolder().toPath().resolve("data").resolve("stations");
        if (!Files.exists(root)) {
            return Map.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String lower = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                        return lower.endsWith(".yml") || lower.endsWith(".yaml");
                    })
                    .toList()) {
                YamlSection state = YamlFiles.load(file.toFile());
                if (!stationType.folderName().equalsIgnoreCase(state.getString("station_type", ""))) {
                    continue;
                }
                StationCoordinates coordinates = StationCoordinates.fromSection(state);
                if (coordinates != null) {
                    rememberStationSource(coordinates, stationSource(state));
                    states.put(coordinates, state);
                }
            }
        } catch (IOException _) {
            return Map.of();
        }
        return states.isEmpty() ? Map.of() : Map.copyOf(states);
    }

    public YamlSection load(StationCoordinates coordinates) {
        if (coordinates == null) {
            return null;
        }
        Path file = pathFor(coordinates);
        YamlSection state = null;
        if (Files.exists(file)) {
            try {
                state = YamlFiles.load(file.toFile());
            } catch (YamlLoadException exception) {
                if (!causedByMissingFile(exception)) {
                    throw exception;
                }
            }
        }
        rememberStationSource(coordinates, stationSource(state));
        return state;
    }

    public void save(StationCoordinates coordinates, Map<String, Object> state) {
        trySave(coordinates, state);
    }

    public boolean trySave(StationCoordinates coordinates, Map<String, Object> state) {
        if (coordinates == null || state == null || state.isEmpty()) {
            return false;
        }
        Map<String, Object> stateWithSource = stateWithRememberedSource(coordinates, state);
        try {
            YamlFiles.save(pathFor(coordinates).toFile(), stateWithSource);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save station state " + coordinates.runtimeKey() + ": " + exception.getMessage());
            return false;
        }
    }

    public void delete(StationCoordinates coordinates) {
        tryDelete(coordinates);
    }

    public boolean tryDelete(StationCoordinates coordinates) {
        if (coordinates == null) {
            return false;
        }
        stationSources.remove(coordinates);
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
        if (asyncFileService == null) {
            return CompletableFuture.completedFuture(trySave(coordinates, state));
        }
        Map<String, Object> stateWithSource = stateWithRememberedSource(coordinates, state);
        Path path = pathFor(coordinates);
        return asyncFileService.write(path, "station-save:" + coordinates.runtimeKey(), () -> {
            try {
                YamlFiles.save(path.toFile(), stateWithSource);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).thenApply(_ -> true).exceptionally(throwable -> {
            plugin.getLogger().warning("Async save failed for station " + coordinates.runtimeKey() + ": " + rootCauseMessage(throwable));
            return false;
        });
    }

    public CompletableFuture<Boolean> deleteAsync(StationCoordinates coordinates) {
        if (coordinates == null) {
            return CompletableFuture.completedFuture(false);
        }
        stationSources.remove(coordinates);
        if (asyncFileService == null) {
            return CompletableFuture.completedFuture(tryDelete(coordinates));
        }
        Path path = pathFor(coordinates);
        return asyncFileService.write(path, "station-delete:" + coordinates.runtimeKey(), () -> {
            try {
                Files.deleteIfExists(path);
                cleanupParents(path.getParent());
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).thenApply(_ -> true).exceptionally(throwable -> {
            plugin.getLogger().warning("Async delete failed for station " + coordinates.runtimeKey() + ": " + rootCauseMessage(throwable));
            return false;
        });
    }

    public CompletableFuture<Void> waitForIdle() {
        if (asyncFileService == null) {
            return CompletableFuture.completedFuture(null);
        }
        return asyncFileService.waitForIdle();
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

    private Map<String, Object> stateWithRememberedSource(StationCoordinates coordinates, Map<String, Object> state) {
        Object rawSource = state.get(STATION_SOURCE_KEY);
        ItemSource existing = ItemSourceUtil.parse(rawSource);
        if (existing != null) {
            rememberStationSource(coordinates, existing);
            return state;
        }
        ItemSource stationSource = rememberedStationSource(coordinates);
        String shorthand = ItemSourceUtil.toShorthand(stationSource);
        if (shorthand == null || shorthand.isBlank()) {
            return state;
        }
        Map<String, Object> copy = new LinkedHashMap<>(state);
        copy.put(STATION_SOURCE_KEY, shorthand);
        return copy;
    }

    private Path pathFor(StationCoordinates coordinates) {
        return plugin.getDataFolder().toPath().resolve(coordinates.relativeDataPath());
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
        Path stationsRoot = plugin.getDataFolder().toPath().resolve("data").resolve("stations");
        Path current = directory;
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
}
