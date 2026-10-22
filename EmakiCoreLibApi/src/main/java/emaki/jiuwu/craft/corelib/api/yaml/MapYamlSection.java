package emaki.jiuwu.craft.corelib.api.yaml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * {@link YamlSection} backed by an in-memory map: a detached snapshot that neither reads nor
 * writes any document.
 *
 * <p>The constructor normalizes the source (string keys, recursively plain maps and lists),
 * reads and writes address entries by dot paths while creating missing intermediate sections on
 * {@link #set}, and returns immutable copies from the collection getters. Each instance owns a
 * plain {@link LinkedHashMap} and is not synchronized; give every thread its own instance (or
 * synchronize externally) when a section is mutated concurrently.
 */
public final class MapYamlSection implements YamlSection {

    private final Map<String, Object> values;

    /** Creates an empty section. */
    public MapYamlSection() {
        this.values = new LinkedHashMap<>();
    }

    /**
     * Creates a section from a source map.
     *
     * @param values source data; {@code null} yields an empty section, keys are stringified,
     *               null keys are skipped, and nested maps and lists are normalized
     *               recursively
     */
    public MapYamlSection(Map<String, ?> values) {
        this.values = normalizeMap(values);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This snapshot unwraps nested nodes into plain maps and lists rather than returning
     * nested sections; ask {@link #getSection} for a section view.
     */
    @Override
    public Object get(String path) {
        if (Texts.isBlank(path)) {
            return this;
        }
        Object current = values;
        for (String key : split(path)) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(key);
            if (current == null) {
                return null;
            }
        }
        return unwrapValue(current);
    }

    /** {@inheritDoc} */
    @Override
    public boolean contains(String path) {
        if (Texts.isBlank(path)) {
            return !values.isEmpty();
        }
        Object current = values;
        String[] parts = split(path);
        for (int index = 0; index < parts.length; index++) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(parts[index])) {
                return false;
            }
            current = map.get(parts[index]);
            if (index < parts.length - 1 && current == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Every value type is accepted: non-text nodes are read with {@code String.valueOf}.
     */
    @Override
    public String getString(String path, String defaultValue) {
        Object value = get(path);
        return value == null ? defaultValue : String.valueOf(value);
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@link Number} nodes are narrowed with {@code intValue()} (fractions truncate) and text
     * nodes must be integer literals.
     */
    @Override
    public Integer getInt(String path, Integer defaultValue) {
        Object value = get(path);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception _) {
            return defaultValue;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>{@link Number} nodes are read with {@code doubleValue()} and text nodes are parsed with
     * {@link Double#parseDouble}.
     */
    @Override
    public Double getDouble(String path, Double defaultValue) {
        Object value = get(path);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception _) {
            return defaultValue;
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>A present non-boolean node is read with {@code Boolean.parseBoolean} rules: only the
     * text {@code "true"} (ignoring case) is {@code true}, every other text becomes
     * {@code false} rather than the default.
     */
    @Override
    public Boolean getBoolean(String path, Boolean defaultValue) {
        Object value = get(path);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Only actual list nodes match; any other node type falls back to the default.
     */
    @Override
    public List<?> getList(String path, List<?> defaultValue) {
        Object value = get(path);
        if (value instanceof List<?> list) {
            return List.copyOf(normalizeList(list));
        }
        return defaultValue == null ? List.of() : List.copyOf(defaultValue);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Null elements are dropped instead of being stringified.
     */
    @Override
    public List<String> getStringList(String path) {
        List<String> result = new ArrayList<>();
        for (Object entry : getList(path, List.of())) {
            if (entry != null) {
                result.add(String.valueOf(entry));
            }
        }
        return List.copyOf(result);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Elements that are not maps after unwrapping are dropped.
     */
    @Override
    public List<Map<?, ?>> getMapList(String path) {
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object entry : getList(path, List.of())) {
            Object normalized = unwrapValue(entry);
            if (normalized instanceof Map<?, ?> map) {
                result.add(Map.copyOf(map));
            }
        }
        return List.copyOf(result);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The result is a fresh copy: writes to it never affect this section.
     */
    @Override
    public YamlSection getSection(String path) {
        if (Texts.isBlank(path)) {
            return this;
        }
        Object value = get(path);
        if (value instanceof Map<?, ?> map) {
            return new MapYamlSection(normalizeMap(map));
        }
        if (value instanceof YamlSection section) {
            return section.copy();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deep mode walks nested maps and returns their dotted routes; every key is stringified.
     */
    @Override
    public Set<String> getKeys(boolean deep) {
        Set<String> keys = new LinkedHashSet<>();
        collectKeys(values, "", deep, keys);
        return Collections.unmodifiableSet(keys);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isString(String path) {
        return get(path) instanceof String;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isSection(String path) {
        return get(path) instanceof Map<?, ?>;
    }

    /**
     * {@inheritDoc}
     *
     * <p>A blank path replaces the whole content: the map is cleared and refilled only when
     * {@code value} is itself a map.
     */
    @Override
    public void set(String path, Object value) {
        if (Texts.isBlank(path)) {
            values.clear();
            if (value instanceof Map<?, ?> map) {
                values.putAll(normalizeMap(map));
            }
            return;
        }
        String[] parts = split(path);
        Map<String, Object> current = values;
        for (int index = 0; index < parts.length - 1; index++) {
            Object nested = current.get(parts[index]);
            if (!(nested instanceof Map<?, ?> map)) {
                Map<String, Object> created = new LinkedHashMap<>();
                current.put(parts[index], created);
                current = created;
                continue;
            }
            Map<String, Object> normalized = normalizeMapReference(map);
            current.put(parts[index], normalized);
            current = normalized;
        }
        String key = parts[parts.length - 1];
        if (value == null) {
            current.remove(key);
            return;
        }
        current.put(key, normalizeValue(value));
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The returned map is a fresh normalized copy; mutating it does not affect this section.
     */
    @Override
    public Map<String, Object> asMap() {
        return normalizeMap(values);
    }

    /** {@inheritDoc} */
    @Override
    public YamlSection copy() {
        return new MapYamlSection(asMap());
    }

    private static void collectKeys(Map<String, Object> source,
            String parentPath,
            boolean deep,
            Set<String> destination) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = parentPath.isBlank() ? entry.getKey() : parentPath + "." + entry.getKey();
            destination.add(key);
            if (deep && entry.getValue() instanceof Map<?, ?> nested) {
                collectKeys(normalizeMap(nested), key, true, destination);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeMapReference(Map<?, ?> map) {
        if (map instanceof LinkedHashMap<?, ?> linkedHashMap) {
            return (Map<String, Object>) linkedHashMap;
        }
        return normalizeMap(map);
    }

    private static String[] split(String path) {
        return path.trim().split("\\.");
    }

    /**
     * Normalizes a map into plain form.
     *
     * <p>Null keys are skipped, the remaining keys are stringified, and nested values are
     * normalized recursively; a {@code null} map yields an empty map.
     *
     * @param map source map; may be {@code null}
     * @return a fresh {@link LinkedHashMap} in the source's iteration order; never {@code null}
     */
    public static Map<String, Object> normalizeMap(Map<?, ?> map) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (map == null) {
            return normalized;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
        }
        return normalized;
    }

    private static List<Object> normalizeList(Collection<?> list) {
        List<Object> normalized = new ArrayList<>();
        if (list == null) {
            return normalized;
        }
        for (Object entry : list) {
            normalized.add(normalizeValue(entry));
        }
        return normalized;
    }

    /**
     * Normalizes one value into plain form.
     *
     * <p>Strings, numbers and booleans pass through unchanged, {@link YamlSection} values become
     * maps, maps and collections are normalized recursively, and any other type is returned
     * as-is.
     *
     * @param value value to normalize; may be {@code null}
     * @return the plain equivalent of the value; may be {@code null}
     */
    public static Object normalizeValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof YamlSection section) {
            return section.asMap();
        }
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (value instanceof Collection<?> collection) {
            return normalizeList(collection);
        }
        return value;
    }

    /**
     * Unwraps section-shaped values into plain maps and lists.
     *
     * <p>Scalar values and unrecognized types pass through unchanged, so the result never holds
     * a nested {@link YamlSection}.
     *
     * @param value value to unwrap; may be {@code null}
     * @return a plain map, list or scalar; may be {@code null}
     */
    public static Object unwrapValue(Object value) {
        if (value instanceof YamlSection section) {
            return section.asMap();
        }
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (value instanceof Collection<?> collection) {
            return normalizeList(collection);
        }
        return value;
    }
}
