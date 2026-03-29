package emaki.jiuwu.craft.forge.loader;

import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.PlayerData;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerDataStore {

    private final EmakiForgePlugin plugin;
    private final Map<String, PlayerData> cache = new LinkedHashMap<>();

    public PlayerDataStore(EmakiForgePlugin plugin) {
        this.plugin = plugin;
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
        return cache.computeIfAbsent(uuid.toString(), this::loadPlayerData);
    }

    public boolean save(UUID uuid) {
        PlayerData data = cache.get(uuid.toString());
        return data != null && save(uuid.toString(), data);
    }

    public int saveAll() {
        int saved = 0;
        for (Map.Entry<String, PlayerData> entry : cache.entrySet()) {
            if (save(entry.getKey(), entry.getValue())) {
                saved++;
            }
        }
        return saved;
    }

    public void clear(UUID uuid) {
        cache.remove(uuid.toString());
    }

    public void recordCraft(UUID uuid, String recipeId) {
        get(uuid).recordCraft(recipeId, Instant.now().toString());
    }

    public boolean hasCrafted(UUID uuid, String recipeId) {
        return get(uuid).hasCrafted(recipeId);
    }

    public int craftCount(UUID uuid, String recipeId) {
        return get(uuid).history(recipeId).craftCount();
    }

    public int guaranteeCounter(UUID uuid, String key) {
        return get(uuid).guaranteeCounter(key);
    }

    public void incrementGuaranteeCounter(UUID uuid, String key) {
        get(uuid).incrementGuaranteeCounter(key);
    }

    public void resetGuaranteeCounter(UUID uuid, String key) {
        get(uuid).resetGuaranteeCounter(key);
    }

    private PlayerData loadPlayerData(String uuid) {
        File file = plugin.dataPath("data", uuid + ".yml").toFile();
        if (!file.exists()) {
            return new PlayerData(uuid);
        }
        return PlayerData.fromConfig(uuid, YamlFiles.load(file));
    }

    private boolean save(String uuid, PlayerData data) {
        File file = plugin.dataPath("data", uuid + ".yml").toFile();
        try {
            YamlFiles.save(file, data.toMap());
            return true;
        } catch (IOException exception) {
            plugin.messageService().warning("console.player_data_save_failed", Map.of(
                "uuid", uuid,
                "error", String.valueOf(exception.getMessage())
            ));
            return false;
        }
    }
}
