package emaki.jiuwu.craft.attribute.service;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.ParentAttributeData;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class ParentAttributeDataStore {

    private final EmakiAttributePlugin plugin;
    private final Map<UUID, ParentAttributeData> cache = new ConcurrentHashMap<>();

    public ParentAttributeDataStore(EmakiAttributePlugin plugin) {
        this.plugin = plugin;
    }

    public ParentAttributeData load(Player player) {
        ParentAttributeData data = load(player.getUniqueId(), player.getName());
        data.name(player.getName());
        ensureParentAttributes(data);
        return data;
    }

    public ParentAttributeData load(UUID uuid, String name) {
        ParentAttributeData data = read(uuid, name);
        ensureParentAttributes(data);
        cache.put(uuid, data);
        return data;
    }

    public ParentAttributeData getOrLoad(UUID uuid) {
        ParentAttributeData existing = cache.get(uuid);
        if (existing != null) {
            ensureParentAttributes(existing);
            return existing;
        }
        Player player = Bukkit.getPlayer(uuid);
        return load(uuid, player == null ? uuid.toString() : player.getName());
    }

    public ParentAttributeData cached(UUID uuid) {
        return uuid == null ? null : cache.get(uuid);
    }

    public void unload(UUID uuid, boolean save) {
        ParentAttributeData data = cache.remove(uuid);
        if (save && data != null) {
            save(data);
        }
    }

    public void saveAll() {
        for (ParentAttributeData data : cache.values()) {
            save(data);
        }
    }

    public void save(ParentAttributeData data) {
        if (data == null || !data.dirty()) {
            return;
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", 1);
        root.put("uuid", data.uuid().toString());
        root.put("name", data.name());
        root.put("available_points", data.availablePoints());
        root.put("reset_points", data.resetPoints());
        Map<String, Object> allocations = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : data.allocations().entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                allocations.put(entry.getKey(), entry.getValue());
            }
        }
        root.put("allocations", allocations);
        root.put("updated_at", data.updatedAt());
        try {
            YamlFiles.save(file(data.uuid()), root);
            data.clearDirty();
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save parent attribute data for " + data.uuid() + ": " + exception.getMessage());
        }
    }

    private ParentAttributeData read(UUID uuid, String name) {
        YamlSection root = YamlFiles.load(file(uuid));
        ParentAttributeData data = new ParentAttributeData(uuid, Texts.isBlank(root.getString("name", null)) ? name : root.getString("name", name));
        data.availablePoints(Math.max(0, root.getInt("available_points", 0)));
        data.resetPoints(Math.max(0, root.getInt("reset_points", 0)));
        YamlSection allocations = root.getSection("allocations");
        if (allocations != null) {
            for (String key : allocations.getKeys(false)) {
                String id = Texts.normalizeId(key);
                int points = Math.max(0, allocations.getInt(key, 0));
                if (Texts.isNotBlank(id) && points > 0) {
                    data.allocations().put(id, points);
                }
            }
        }
        data.clearDirty();
        return data;
    }

    private void ensureParentAttributes(ParentAttributeData data) {
        if (data == null || plugin.attributeRegistry() == null) {
            return;
        }
        data.allocations().entrySet().removeIf(entry -> {
            AttributeDefinition definition = plugin.attributeRegistry().get(entry.getKey());
            boolean remove = definition == null || !definition.parentAttribute() || entry.getValue() == null || entry.getValue() <= 0;
            if (remove) {
                data.markDirty();
            }
            return remove;
        });
    }

    private File file(UUID uuid) {
        return plugin.getDataFolder().toPath().resolve("data").resolve("parent_attributes").resolve(uuid + ".yml").toFile();
    }
}
