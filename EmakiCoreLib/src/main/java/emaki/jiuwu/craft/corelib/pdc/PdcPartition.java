package emaki.jiuwu.craft.corelib.pdc;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.bukkit.NamespacedKey;

public record PdcPartition(String namespace, String path) {

    /**
     * 分区与字段的连接符。
     *
     * <p>历史版本用 {@code '.'}，但 Bukkit 的 {@code YamlConfiguration} 把 {@code '.'}
     * 当路径分隔符，导致带点的 PDC 键无法在 YAML 里表达——第三方插件（NeigeItems 等）
     * 想手写 EA 属性只能改用 Lore。改成 {@code '_'} 后键名可直接写进 YAML。
     *
     * <p>老键的迁移由 {@code PdcKeyMigration} 负责，不在这里做兼容。
     */
    public static final String SEPARATOR = "_";

    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("[^a-z0-9._-]");
    private static final Pattern KEY_PATTERN = Pattern.compile("[^a-z0-9/._-]");

    public PdcPartition  {
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
        return path + SEPARATOR + normalizedField;
    }

    public PdcPartition child(String childPath) {
        if (childPath == null || childPath.isBlank()) {
            return this;
        }
        if (path.isEmpty()) {
            return new PdcPartition(namespace, childPath);
        }
        return new PdcPartition(namespace, path + SEPARATOR + childPath);
    }

    private static String normalizeNamespace(String value) {
        String result = Objects.requireNonNullElse(value, "").trim().toLowerCase(Locale.ROOT);
        result = NAMESPACE_PATTERN.matcher(result).replaceAll("_");
        return result.isBlank() ? "emaki" : result;
    }

    private static String normalizePath(String value) {
        String result = Objects.requireNonNullElse(value, "").trim().toLowerCase(Locale.ROOT);
        result = KEY_PATTERN.matcher(result).replaceAll("_");
        // KEY_PATTERN 仍允许 '.'：迁移表需要原样重建历史键，压缩 '..' 保持与历史行为一致。
        while (result.contains("..")) {
            result = result.replace("..", ".");
        }
        // '__' 故意不压缩。'_' 现在是分段连接符，压缩会让 child("a").child("_b")
        // 与 child("a_b") 撞成同一个键，导致两份数据互相覆盖。
        // 首尾也只裁剪 '.' 而不裁剪 '_'，理由相同：裁掉会让 "_foo" 与 "foo" 撞键。
        if (result.startsWith(".")) {
            result = result.substring(1);
        }
        if (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
