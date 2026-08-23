package emaki.jiuwu.craft.item.api;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

/** Stable typed key for an item state field. */
public record ItemStateKey<T>(String namespace, String partition, String key, ItemStateType type) {
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9][a-z0-9._/-]*");

    public ItemStateKey {
        namespace = normalize(namespace, "namespace");
        partition = normalize(partition, "partition");
        key = normalize(key, "key");
        type = Objects.requireNonNull(type, "type");
        if (!ItemStateSchema.NAMESPACE.equals(namespace)) {
            throw new IllegalArgumentException("Unsupported item-state namespace: " + namespace);
        }
        if (!ItemStateSchema.PARTITION.equals(partition)) {
            throw new IllegalArgumentException("Unsupported item-state partition: " + partition);
        }
        if (!TOKEN.matcher(key).matches() || key.contains("/")) {
            throw new IllegalArgumentException("Invalid item-state key: " + key);
        }
    }

    public ItemStateKey(String partition, String key, ItemStateType type) {
        this("emaki", partition, key, type);
    }

    /**
     * {@return 分区与字段拼成的 PDC 键路径}
     *
     * <p>用 {@code '_'} 连接。历史版本用 {@code '.'}，但 Bukkit 的
     * {@code YamlConfiguration} 把 {@code '.'} 当路径分隔符，带点的键无法在 YAML 里表达。
     *
     * <p>注意 {@code key} 本身仍允许含点（服主可在物品配置里定义 {@code my.field}），
     * 那个点不是连接符，不会被替换。
     */
    public String path() {
        return partition + "_" + key;
    }



    public String namespacedPath() {
        return namespace + ":" + path();
    }

    @SuppressWarnings("unchecked")
    public Class<T> javaType() {
        return (Class<T>) type.javaType();
    }

    private static String normalize(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
