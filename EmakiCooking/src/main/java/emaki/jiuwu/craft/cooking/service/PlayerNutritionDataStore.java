package emaki.jiuwu.craft.cooking.service;

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
import emaki.jiuwu.craft.cooking.model.NutritionTypeConfig;
import emaki.jiuwu.craft.cooking.model.PlayerNutritionData;

/**
 * 玩家营养数据存储。每个玩家一个 {@code data/nutrition/<uuid>.yml} 文件。
 *
 * <p>实现参考 EmakiLevel 的 {@code PlayerLevelDataStore}：内存缓存 + 脏标记落盘，
 * 加载时按当前营养类型补齐缺失项的默认值。</p>
 */
public final class PlayerNutritionDataStore {

    private static final int SCHEMA_VERSION = 1;

    private final JavaPlugin plugin;
    private final Map<UUID, PlayerNutritionData> cache = new ConcurrentHashMap<>();

    public PlayerNutritionDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerNutritionData load(Player player, Map<String, NutritionTypeConfig> types) {
        PlayerNutritionData data = load(player.getUniqueId(), player.getName(), types);
        data.name(player.getName());
        return data;
    }

    public PlayerNutritionData load(UUID uuid, String name, Map<String, NutritionTypeConfig> types) {
        PlayerNutritionData data = read(uuid, name, types);
        cache.put(uuid, data);
        return data;
    }

    public PlayerNutritionData getOrLoad(UUID uuid, Map<String, NutritionTypeConfig> types) {
        PlayerNutritionData existing = cache.get(uuid);
        if (existing != null) {
            ensureTypes(existing, types);
            return existing;
        }
        Player player = Bukkit.getPlayer(uuid);
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return load(uuid, player != null ? player.getName() : offline.getName(), types);
    }

    public PlayerNutritionData cached(UUID uuid) {
        return cache.get(uuid);
    }

    public void unload(UUID uuid, boolean save) {
        PlayerNutritionData data = cache.remove(uuid);
        if (save && data != null) {
            save(data);
        }
    }

    public void saveAll() {
        for (PlayerNutritionData data : cache.values()) {
            save(data);
        }
    }

    /**
     * 重新加载所有在线玩家缓存中缺失的营养类型默认值（用于 reload 后补齐新增类型）。
     */
    public void ensureTypesForCached(Map<String, NutritionTypeConfig> types) {
        for (PlayerNutritionData data : cache.values()) {
            ensureTypes(data, types);
        }
    }

    public void ensureTypes(PlayerNutritionData data, Map<String, NutritionTypeConfig> types) {
        if (data == null || types == null) {
            return;
        }
        for (NutritionTypeConfig type : types.values()) {
            if (!data.has(type.id())) {
                data.set(type.id(), type.defaultValue());
            }
        }
    }

    public void save(PlayerNutritionData data) {
        if (data == null || !data.dirty()) {
            return;
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", SCHEMA_VERSION);
        root.put("uuid", data.uuid().toString());
        root.put("name", data.name());
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : data.values().entrySet()) {
            values.put(entry.getKey(), entry.getValue());
        }
        root.put("values", values);
        try {
            YamlFiles.save(file(data.uuid()), root);
            data.clearDirty();
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save EmakiCooking nutrition data for " + data.uuid() + ": " + exception.getMessage());
        }
    }

    private PlayerNutritionData read(UUID uuid, String name, Map<String, NutritionTypeConfig> types) {
        File file = file(uuid);
        YamlSection root = YamlFiles.load(file);
        String storedName = root.getString("name", name);
        PlayerNutritionData data = new PlayerNutritionData(uuid, Texts.isBlank(storedName) ? name : storedName);
        YamlSection values = root.getSection("values");
        if (values != null) {
            for (String typeId : values.getKeys(false)) {
                data.set(Texts.normalizeId(typeId), values.getDouble(typeId, 0D));
            }
        }
        ensureTypes(data, types);
        data.clearDirty();
        return data;
    }

    private File file(UUID uuid) {
        return plugin.getDataFolder().toPath().resolve("data").resolve("nutrition").resolve(uuid + ".yml").toFile();
    }
}
