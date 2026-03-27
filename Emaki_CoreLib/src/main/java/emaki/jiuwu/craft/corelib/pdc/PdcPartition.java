package emaki.jiuwu.craft.corelib.pdc;

import java.util.Objects;
import java.util.regex.Pattern;
import org.bukkit.NamespacedKey;

public record PdcPartition(String namespace, String path) {

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[^a-z0-9._-]");
    private static final Pattern KEY_PATTERN = Pattern.compile("[^a-z0-9/._-]");

    public PdcPartition {
        namespace = normalizeNamespace(namespace);
        path = normalizePath(path);
    }

    public NamespacedKey key() {
        return key(path);
    }

    public NamespacedKey key(String field) {
        return Objects.requireNonNull(NamespacedKey.fromString(namespace + ":" + qualifiedPath(field)));
    }

    public String qualifiedPath(String field) {
        String normalizedField = normalizePath(field);
        if (normalizedField.isEmpty()) {
            return path;
        }
        if (path.isEmpty()) {
            return normalizedField;
        }
        return path + "." + normalizedField;
    }

    public PdcPartition child(String childPath) {
        if (childPath == null || childPath.isBlank()) {
            return this;
        }
        if (path.isEmpty()) {
            return new PdcPartition(namespace, childPath);
        }
        return new PdcPartition(namespace, path + "." + childPath);
    }

    private static String normalizeNamespace(String value) {
        String result = Objects.requireNonNullElse(value, "").trim().toLowerCase();
        result = NAMESPACE_PATTERN.matcher(result).replaceAll("_");
        return result.isBlank() ? "emaki" : result;
    }

    private static String normalizePath(String value) {
        String result = Objects.requireNonNullElse(value, "").trim().toLowerCase();
        result = KEY_PATTERN.matcher(result).replaceAll("_");
        while (result.contains("..")) {
            result = result.replace("..", ".");
        }
        while (result.contains("__")) {
            result = result.replace("__", "_");
        }
        if (result.startsWith(".")) {
            result = result.substring(1);
        }
        if (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
