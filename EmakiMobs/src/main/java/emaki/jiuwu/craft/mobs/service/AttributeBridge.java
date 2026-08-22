package emaki.jiuwu.craft.mobs.service;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Map;

public final class AttributeBridge {

    private static final String KEY_PREFIX = "attr_";

    private final Plugin plugin;

    public AttributeBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    public void apply(LivingEntity entity, Map<String, Double> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        for (Map.Entry<String, Double> entry : attributes.entrySet()) {
            String key = sanitizeKey(entry.getKey());
            if (key.isEmpty()) {
                continue;
            }
            NamespacedKey nsKey = new NamespacedKey(plugin, KEY_PREFIX + key);
            pdc.set(nsKey, PersistentDataType.DOUBLE, entry.getValue());
        }
    }

    public double read(LivingEntity entity, String attributeId, double defaultValue) {
        String key = sanitizeKey(attributeId);
        if (key.isEmpty()) {
            return defaultValue;
        }
        NamespacedKey nsKey = new NamespacedKey(plugin, KEY_PREFIX + key);
        Double value = entity.getPersistentDataContainer().get(nsKey, PersistentDataType.DOUBLE);
        return value != null ? value : defaultValue;
    }

    private static String sanitizeKey(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        return id.toLowerCase(Locale.ROOT)
                .replace(" ", "_")
                .replaceAll("[^a-z0-9_.\\-]", "_");
    }
}
