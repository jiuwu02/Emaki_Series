package emaki.jiuwu.craft.corelib.api.item;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Package-private helpers that normalize and defensively copy the plain data behind the item API
 * types ({@link ItemBuildIssue}, {@link ItemComponentCapability}, {@link ItemComponentPatch} and
 * {@link ConfiguredItemDefinition}).
 *
 * <p>Accepted values are strings, boxed primitives, {@link BigInteger}, {@link BigDecimal},
 * maps with non-null keys, and lists of the same; anything else is rejected with
 * {@link IllegalArgumentException} instead of being silently stringified.
 */
final class PlainItemData {

    private static final Pattern COMPONENT_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private PlainItemData() {
    }

    /**
     * Normalizes a component id to the {@code namespace:key} form.
     *
     * @param raw raw id; a missing namespace is filled in with {@code minecraft:}
     * @return the trimmed, {@link Locale#ROOT}-lowercased id matching
     *         {@code [a-z0-9_.-]+:[a-z0-9_./-]+}
     * @throws IllegalArgumentException when {@code raw} is {@code null} or the normalized id
     *         does not match the required shape
     */
    static String componentId(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("组件 ID 不能为 null。");
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        if (!COMPONENT_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("无效的带命名空间组件 ID: " + raw);
        }
        return normalized;
    }

    /**
     * Deep-copies a plain data value into an immutable structure.
     *
     * <p>Strings, boxed primitives, {@link BigInteger} and {@link BigDecimal} are returned
     * unchanged because they are immutable, {@code Character} becomes its string form, and maps
     * and lists are rebuilt as unmodifiable containers with every nested value copied the same
     * way. Map keys are converted with {@code String.valueOf}.
     *
     * @param value value to copy; {@code null} is returned as-is
     * @return the copied value, never a mutable map or list
     * @throws IllegalArgumentException if a map holds a {@code null} key, or the value (or a
     *         nested value) has a type this helper does not support
     */
    static Object copy(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double
                || value instanceof BigInteger || value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Character character) {
            return character.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    throw new IllegalArgumentException("纯物品数据映射不能包含 null 键。");
                }
                copied.put(String.valueOf(entry.getKey()), copy(entry.getValue()));
            }
            return Collections.unmodifiableMap(copied);
        }
        if (value instanceof List<?> list) {
            List<Object> copied = new ArrayList<>(list.size());
            for (Object entry : list) {
                copied.add(copy(entry));
            }
            return Collections.unmodifiableList(copied);
        }
        throw new IllegalArgumentException("不支持的物品数据值类型: " + value.getClass().getName());
    }

    /**
     * Copies a component-patch map under normalized component ids.
     *
     * @param source source map; {@code null} or empty yields {@link Map#of()}
     * @return an unmodifiable copy that keeps the source's iteration order, with every key
     *         normalized through {@link #componentId} and every value copied through
     *         {@link ItemComponentPatch#copy()}
     * @throws IllegalArgumentException if a value is {@code null} or a key is not a valid
     *         component id
     */
    static Map<String, ItemComponentPatch> componentMap(Map<String, ItemComponentPatch> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, ItemComponentPatch> copied = new LinkedHashMap<>();
        for (Map.Entry<String, ItemComponentPatch> entry : source.entrySet()) {
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("组件补丁不能为 null: " + entry.getKey());
            }
            copied.put(componentId(entry.getKey()), entry.getValue().copy());
        }
        return Collections.unmodifiableMap(copied);
    }
}
