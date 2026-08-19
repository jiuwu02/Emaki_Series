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

    public String path() {
        return partition + "." + key;
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
