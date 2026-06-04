package emaki.jiuwu.craft.level.service;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelData;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;

public final class PlayerLevelDataStore {

    private final JavaPlugin plugin;
    private final Map<UUID, PlayerLevelData> cache = new ConcurrentHashMap<>();

    public PlayerLevelDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerLevelData load(Player player, Map<String, LevelTypeConfig> types) {
        PlayerLevelData data = load(player.getUniqueId(), player.getName(), types);
        data.name(player.getName());
        return data;
    }

    public PlayerLevelData load(UUID uuid, String name, Map<String, LevelTypeConfig> types) {
        PlayerLevelData data = read(uuid, name, types);
        cache.put(uuid, data);
        return data;
    }

    public PlayerLevelData getOrLoad(UUID uuid, Map<String, LevelTypeConfig> types) {
        PlayerLevelData existing = cache.get(uuid);
        if (existing != null) {
            ensureTypes(existing, types);
            return existing;
        }
        Player player = Bukkit.getPlayer(uuid);
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return load(uuid, player != null ? player.getName() : offline.getName(), types);
    }

    public PlayerLevelData cached(UUID uuid) {
        return cache.get(uuid);
    }

    public Map<UUID, PlayerLevelData> cachedData() {
        return Map.copyOf(cache);
    }

    public void unload(UUID uuid, boolean save) {
        PlayerLevelData data = cache.remove(uuid);
        if (save && data != null) {
            save(data);
        }
    }

    public void saveAll() {
        for (PlayerLevelData data : cache.values()) {
            save(data);
        }
    }

    public void ensureTypes(PlayerLevelData data, Map<String, LevelTypeConfig> types) {
        if (data == null || types == null) {
            return;
        }
        for (LevelTypeConfig type : types.values()) {
            data.levels().computeIfAbsent(type.id(), _ -> {
                data.markDirty();
                return new PlayerLevelEntry(type.startLevel(), 0D, 0D, System.currentTimeMillis());
            });
        }
    }

    public void save(PlayerLevelData data) {
        if (data == null || !data.dirty()) {
            return;
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", 1);
        root.put("uuid", data.uuid().toString());
        root.put("name", data.name());
        Map<String, Object> levels = new LinkedHashMap<>();
        for (Map.Entry<String, PlayerLevelEntry> entry : data.levels().entrySet()) {
            PlayerLevelEntry value = entry.getValue();
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("level", value.level());
            section.put("exp", value.exp());
            section.put("total_exp", value.totalExp());
            section.put("updated_at", value.updatedAt());
            levels.put(entry.getKey(), section);
        }
        root.put("levels", levels);
        try {
            YamlFiles.save(file(data.uuid()), root);
            data.clearDirty();
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save EmakiLevel data for " + data.uuid() + ": " + exception.getMessage());
        }
    }

    private PlayerLevelData read(UUID uuid, String name, Map<String, LevelTypeConfig> types) {
        File file = file(uuid);
        YamlSection root = YamlFiles.load(file);
        PlayerLevelData data = new PlayerLevelData(uuid, Texts.isBlank(root.getString("name", null)) ? name : root.getString("name", name));
        YamlSection levels = root.getSection("levels");
        if (levels != null) {
            for (String typeId : levels.getKeys(false)) {
                YamlSection section = levels.getSection(typeId);
                if (section == null) {
                    continue;
                }
                data.levels().put(Texts.normalizeId(typeId), new PlayerLevelEntry(
                        section.getInt("level", 1),
                        section.getDouble("exp", 0D),
                        section.getDouble("total_exp", 0D),
                        Math.round(section.getDouble("updated_at", (double) System.currentTimeMillis()))
                ));
            }
        }
        ensureTypes(data, types);
        data.clearDirty();
        return data;
    }

    private File file(UUID uuid) {
        return plugin.getDataFolder().toPath().resolve("data").resolve(uuid + ".yml").toFile();
    }
}
