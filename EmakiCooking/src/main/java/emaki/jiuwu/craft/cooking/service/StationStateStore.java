package emaki.jiuwu.craft.cooking.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class StationStateStore {

    private final JavaPlugin plugin;
    private final AsyncFileService asyncFileService;

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
                        String lower = path.getFileName().toString().toLowerCase();
                        return lower.endsWith(".yml") || lower.endsWith(".yaml");
                    })
                    .toList()) {
                YamlSection state = YamlFiles.load(file.toFile());
                if (!stationType.folderName().equalsIgnoreCase(state.getString("station_type", ""))) {
                    continue;
                }
                StationCoordinates coordinates = StationCoordinates.fromSection(state);
                if (coordinates != null) {
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
        return Files.exists(file) ? YamlFiles.load(file.toFile()) : null;
    }

    public void save(StationCoordinates coordinates, Map<String, Object> state) {
        trySave(coordinates, state);
    }

    public boolean trySave(StationCoordinates coordinates, Map<String, Object> state) {
        if (coordinates == null || state == null || state.isEmpty()) {
            return false;
        }
        try {
            YamlFiles.save(pathFor(coordinates).toFile(), state);
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
        Path path = pathFor(coordinates);
        return asyncFileService.write(path, "station-save:" + coordinates.runtimeKey(), () -> {
            try {
                YamlFiles.save(path.toFile(), state);
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


    private Path pathFor(StationCoordinates coordinates) {
        return plugin.getDataFolder().toPath().resolve(coordinates.relativeDataPath());
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
