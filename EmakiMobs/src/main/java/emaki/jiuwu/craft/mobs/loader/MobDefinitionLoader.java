package emaki.jiuwu.craft.mobs.loader;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MobDefinitionLoader {

    private final Plugin plugin;

    public MobDefinitionLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, MobSpec> loadAll() {
        Map<String, MobSpec> result = new HashMap<>();
        File mobsDir = new File(plugin.getDataFolder(), "mobs");
        if (!mobsDir.isDirectory()) {
            return result;
        }
        File[] files = mobsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return result;
        }
        for (File file : files) {
            MobSpec spec = parseFile(file);
            if (spec != null) {
                result.put(spec.id(), spec);
            }
        }
        return result;
    }

    private MobSpec parseFile(File file) {
        var config = YamlConfiguration.loadConfiguration(file);
        String id = config.getString("id");
        if (id == null || id.isBlank()) {
            plugin.getLogger().warning("Mob file '" + file.getName() + "' missing 'id' field, skipping.");
            return null;
        }
        String typeName = config.getString("type", id);
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(typeName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(
                    "Unknown entity type '" + typeName + "' in '" + file.getName() + "', skipping.");
            return null;
        }
        String displayName = config.getString("display_name");
        int experience = config.getInt("experience", 0);
        Map<String, Object> components = extractSection(config, "components");
        Map<String, Double> attributes = extractDoubleSection(config, "attributes");
        return new MobSpec(id, entityType, displayName, components, attributes, experience);
    }

    private Map<String, Object> extractSection(YamlConfiguration config, String sectionKey) {
        Map<String, Object> result = new HashMap<>();
        var section = config.getConfigurationSection(sectionKey);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                result.put(key, section.get(key));
            }
        }
        return result;
    }

    private Map<String, Double> extractDoubleSection(YamlConfiguration config, String sectionKey) {
        Map<String, Double> result = new HashMap<>();
        var section = config.getConfigurationSection(sectionKey);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Object val = section.get(key);
                if (val instanceof Number num) {
                    result.put(key, num.doubleValue());
                }
            }
        }
        return result;
    }
}
