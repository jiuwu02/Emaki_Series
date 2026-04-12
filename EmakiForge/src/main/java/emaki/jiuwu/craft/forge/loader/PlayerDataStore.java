package emaki.jiuwu.craft.forge.loader;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.PlayerData;

public final class PlayerDataStore {

    private final EmakiForgePlugin plugin;
    private final Supplier<AsyncYamlFiles> asyncYamlFilesSupplier;
    private final PlayerDataCache cache = new PlayerDataCache();

    public PlayerDataStore(EmakiForgePlugin plugin, Supplier<AsyncYamlFiles> asyncYamlFilesSupplier) {
        this.plugin = plugin;
        this.asyncYamlFilesSupplier = asyncYamlFilesSupplier;
    }

    public void load() {
        File directory = plugin.dataPath("data").toFile();
        try {
            YamlFiles.ensureDirectory(directory.toPath());
        } catch (IOException exception) {
            plugin.messageService().warning("console.player_data_directory_create_failed", Map.of(
                    "path", directory.getPath()
            ));
        }
    }

    public PlayerData get(UUID uuid) {
        return cache.getOrLoad(uuid.toString(), () -> loadPlayerData(uuid.toString()));
    }

    public boolean save(UUID uuid) {
        return saveAsync(uuid).join();
    }

    public int saveAll() {
        return saveAllAsync().join();
    }

    public CompletableFuture<Boolean> saveAsync(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(false);
        }
        String key = uuid.toString();
        PlayerData snapshot = cache.snapshot(key);
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
        PlayerData snapshot = cache.snapshot(key);
        if (snapshot == null) {
            cache.remove(key);
            return CompletableFuture.completedFuture(false);
        }
        return saveAsync(key, snapshot)
                .thenApply(saved -> {
                    if (saved) {
                        cache.remove(key);
                    }
                    return saved;
                });
    }

    public CompletableFuture<Integer> saveAllAsync() {
        Map<String, PlayerData> dirtyEntries = cache.snapshotDirtyEntries();
        if (dirtyEntries.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (Map.Entry<String, PlayerData> entry : dirtyEntries.entrySet()) {
            futures.add(saveAsync(entry.getKey(), entry.getValue()));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    int saved = 0;
                    int index = 0;
                    for (Map.Entry<String, PlayerData> entry : dirtyEntries.entrySet()) {
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

    public void recordCraft(UUID uuid, String recipeId) {
        if (uuid == null) {
            return;
        }
        cache.update(uuid.toString(), () -> loadPlayerData(uuid.toString()), data -> data.recordCraft(recipeId, Instant.now().toString()));
    }

    public boolean hasCrafted(UUID uuid, String recipeId) {
        if (uuid == null) {
            return false;
        }
        return cache.read(uuid.toString(), () -> loadPlayerData(uuid.toString()), data -> data.hasCrafted(recipeId));
    }

    public int craftCount(UUID uuid, String recipeId) {
        if (uuid == null) {
            return 0;
        }
        return cache.read(uuid.toString(), () -> loadPlayerData(uuid.toString()), data -> data.history(recipeId).craftCount());
    }

    public int guaranteeCounter(UUID uuid, String key) {
        if (uuid == null) {
            return 0;
        }
        return cache.read(uuid.toString(), () -> loadPlayerData(uuid.toString()), data -> data.guaranteeCounter(key));
    }

    public void incrementGuaranteeCounter(UUID uuid, String key) {
        if (uuid == null) {
            return;
        }
        cache.update(uuid.toString(), () -> loadPlayerData(uuid.toString()), data -> data.incrementGuaranteeCounter(key));
    }

    public void resetGuaranteeCounter(UUID uuid, String key) {
        if (uuid == null) {
            return;
        }
        cache.update(uuid.toString(), () -> loadPlayerData(uuid.toString()), data -> data.resetGuaranteeCounter(key));
    }

    private PlayerData loadPlayerData(String uuid) {
        File file = plugin.dataPath("data", uuid + ".yml").toFile();
        if (!file.exists()) {
            return new PlayerData(uuid);
        }
        return PlayerData.fromConfig(uuid, YamlFiles.load(file));
    }

    private CompletableFuture<Boolean> saveAsync(String uuid, PlayerData snapshot) {
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
        plugin.messageService().warning("console.player_data_save_failed", Map.of(
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
