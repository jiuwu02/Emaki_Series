package emaki.jiuwu.craft.mobs.service;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Map;

/**
 * 将 mob 定义中的 EmakiAttribute 自定义属性值写入生物实体的 PDC。
 *
 * <p>属性 ID 在生物生成时通过 {@link #apply(LivingEntity, Map)} 写入实体的
 * {@link PersistentDataContainer}，键格式为 {@code emakimobs:attr_<attribute_id>}，
 * 类型为 {@link PersistentDataType#DOUBLE}。
 *
 * <p>EmakiAttribute 或其他系统可在伤害计算时通过 {@link #read(LivingEntity, String, double)}
 * 读取这些值，从而对自定义怪物应用 EA 属性体系（攻击力、防御力、暴击率等）。
 *
 * <p>此类不依赖 EmakiAttribute 运行时，以软依赖方式工作：
 * EA 不存在时，写入的 PDC 数据不产生副作用；EA 存在时可直接读取。
 *
 * <p><strong>线程要求：</strong>调用方必须在实体的 owner 线程上调用。
 */
public final class AttributeBridge {

    private static final String KEY_PREFIX = "attr_";

    private final Plugin plugin;

    public AttributeBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 将 mob 定义的 EA 属性值批量写入实体 PDC。
     *
     * <p>若 {@code attributes} 为空或 {@code null}，调用无副作用。
     *
     * @param entity     目标生物实体
     * @param attributes 属性 ID → 数值 映射（来自 {@code MobSpec.attributes()}）
     */
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

    /**
     * 从实体 PDC 读取指定 EA 属性的值。
     *
     * @param entity      目标生物实体
     * @param attributeId EA 属性 ID
     * @param defaultValue 属性不存在时的默认值
     * @return PDC 中存储的属性值，或 {@code defaultValue}
     */
    public double read(LivingEntity entity, String attributeId, double defaultValue) {
        String key = sanitizeKey(attributeId);
        if (key.isEmpty()) {
            return defaultValue;
        }
        NamespacedKey nsKey = new NamespacedKey(plugin, KEY_PREFIX + key);
        Double value = entity.getPersistentDataContainer().get(nsKey, PersistentDataType.DOUBLE);
        return value != null ? value : defaultValue;
    }

    // ── 私有方法 ────────────────────────────────────────────────────────────

    /**
     * 将属性 ID 规范化为合法的 NamespacedKey 键名。
     *
     * <p>转为小写，空格替换为下划线，去除 {@code [a-z0-9_.-]} 以外的字符。
     */
    private static String sanitizeKey(String id) {
        if (id == null || id.isBlank()) {
            return "";
        }
        return id.toLowerCase(Locale.ROOT)
                .replace(" ", "_")
                .replaceAll("[^a-z0-9_.\\-]", "_");
    }
}
