package emaki.jiuwu.craft.codex.store;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;

/**
 * Persistent per-player unlock store backed by one YAML file per player under the
 * plugin {@code data/} directory. All disk IO is routed through corelib's
 * {@link AsyncYamlFiles} so the main thread never blocks; the store falls back to
 * synchronous IO when the async service is unavailable.
 */
public final class PlayerUnlockStore {

    private final EmakiCodexPlugin plugin;
    private final Supplier<AsyncYamlFiles> asyncYamlFilesSupplier;
    private final PlayerUnlockCache cache = new PlayerUnlockCache();

    public PlayerUnlockStore(EmakiCodexPlugin plugin, Supplier<AsyncYamlFiles> asyncYamlFilesSupplier) {
        this.plugin = plugin;
        this.asyncYamlFilesSupplier = asyncYamlFilesSupplier;
    }

    public void load() {
        File directory = plugin.dataPath("data").toFile();
        try {
            YamlFiles.ensureDirectory(directory.toPath());
        } catch (IOException exception) {
            plugin.messageService().warning("console.unlock_directory_create_failed", Map.of(
                    "path", directory.getPath()
            ));
        }
    }

    /** {@return true when the recipe became newly unlocked for the player} */
    public boolean unlock(UUID uuid, String recipeId) {
        if (uuid == null) {
            return false;
        }
        boolean[] changed = {false};
        cache.update(uuid.toString(), () -> loadPlayerData(uuid.toString()),
                data -> changed[0] = data.unlock(recipeId));
        return changed[0];
    }

    /** {@return true when the recipe was unlocked and is now locked for the player} */
    public boolean lock(UUID uuid, String recipeId) {
        if (uuid == null) {
            return false;
        }
        boolean[] changed = {false};
        cache.update(uuid.toString(), () -> loadPlayerData(uuid.toString()),
                data -> changed[0] = data.lock(recipeId));
        return changed[0];
    }

    public boolean isUnlocked(UUID uuid, String recipeId) {
        if (uuid == null) {
            return false;
        }
        return cache.read(uuid.toString(), () -> loadPlayerData(uuid.toString()),
                data -> data.isUnlocked(recipeId));
    }

    public Set<String> unlockedRecipes(UUID uuid) {
        if (uuid == null) {
            return Set.of();
        }
        return cache.read(uuid.toString(), () -> loadPlayerData(uuid.toString()),
                PlayerUnlockData::unlockedRecipes);
    }

    public CompletableFuture<Boolean> saveAsync(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(false);
        }
        String key = uuid.toString();
        PlayerUnlockData snapshot = cache.snapshot(key);
        return snapshot == null
                ? CompletableFuture.completedFuture(false)
                : saveAsync(key, snapshot).thenApply(saved -> {
                    if (saved) {
                        cache.markClean(key);
                    }
                    return saved;
                });
    }

    public CompletableFuture<Boolean> saveAndClearAsync(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(false);
        }
        String key = uuid.toString();
        PlayerUnlockData snapshot = cache.snapshot(key);
        if (snapshot == null) {
            cache.remove(key);
            return CompletableFuture.completedFuture(false);
        }
        return saveAsync(key, snapshot).thenApply(saved -> {
            if (saved) {
                cache.remove(key);
            }
            return saved;
        });
    }

    public CompletableFuture<Integer> saveAllAsync() {
        Map<String, PlayerUnlockData> dirtyEntries = cache.snapshotDirtyEntries();
        if (dirtyEntries.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (Map.Entry<String, PlayerUnlockData> entry : dirtyEntries.entrySet()) {
            futures.add(saveAsync(entry.getKey(), entry.getValue()));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    int saved = 0;
                    int index = 0;
                    for (Map.Entry<String, PlayerUnlockData> entry : dirtyEntries.entrySet()) {
                        if (Boolean.TRUE.equals(futures.get(index++).join())) {
                            cache.markClean(entry.getKey());
                            saved++;
                        }
                    }
                    return saved;
                });
    }

    public CompletableFuture<Void> waitForPendingSaves() {
        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        if (asyncYamlFiles == null) {
            return CompletableFuture.completedFuture(null);
        }
        return asyncYamlFiles.waitForIdle().exceptionally(throwable -> null);
    }

    public void clear(UUID uuid) {
        if (uuid != null) {
            cache.remove(uuid.toString());
        }
    }

    private PlayerUnlockData loadPlayerData(String uuid) {
        File file = plugin.dataPath("data", uuid + ".yml").toFile();
        if (!file.exists()) {
            return new PlayerUnlockData(uuid);
        }
        return PlayerUnlockData.fromConfig(uuid, YamlFiles.load(file));
    }

    private CompletableFuture<Boolean> saveAsync(String uuid, PlayerUnlockData snapshot) {
        return saveAsync(uuid, snapshot == null ? Map.of() : snapshot.toMap());
    }

    private CompletableFuture<Boolean> saveAsync(String uuid, Map<String, Object> dataSnapshot) {
        File file = plugin.dataPath("data", uuid + ".yml").toFile();
        AsyncYamlFiles asyncYamlFiles = asyncYamlFiles();
        if (asyncYamlFiles == null) {
            try {
                YamlFiles.save(file, dataSnapshot);
                return CompletableFuture.completedFuture(true);
            } catch (IOException exception) {
                logSaveFailure(uuid, exception);
                return CompletableFuture.completedFuture(false);
            }
        }
        return asyncYamlFiles.save(file, dataSnapshot)
                .thenApply(ignored -> true)
                .exceptionally(throwable -> {
                    logSaveFailure(uuid, unwrap(throwable));
                    return false;
                });
    }

    private AsyncYamlFiles asyncYamlFiles() {
        return asyncYamlFilesSupplier == null ? null : asyncYamlFilesSupplier.get();
    }

    private void logSaveFailure(String uuid, Throwable throwable) {
        plugin.messageService().warning("console.unlock_save_failed", Map.of(
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
