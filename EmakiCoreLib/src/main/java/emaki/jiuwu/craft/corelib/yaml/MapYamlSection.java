package emaki.jiuwu.craft.corelib.yaml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class MapYamlSection implements YamlSection {

    private final Map<String, Object> values;

    public MapYamlSection() {
        this.values = new LinkedHashMap<>();
    }

    public MapYamlSection(Map<String, ?> values) {
        this.values = normalizeMap(values);
    }

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

    @Override
    public String getString(String path, String defaultValue) {
        Object value = get(path);
        return value == null ? defaultValue : String.valueOf(value);
    }

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

    @Override
    public List<?> getList(String path, List<?> defaultValue) {
        Object value = get(path);
        if (value instanceof List<?> list) {
            return List.copyOf(normalizeList(list));
        }
        return defaultValue == null ? List.of() : List.copyOf(defaultValue);
    }

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

    @Override
    public Set<String> getKeys(boolean deep) {
        Set<String> keys = new LinkedHashSet<>();
        collectKeys(values, "", deep, keys);
        return Set.copyOf(keys);
    }

    @Override
    public boolean isString(String path) {
        return get(path) instanceof String;
    }

    @Override
    public boolean isSection(String path) {
        return get(path) instanceof Map<?, ?>;
    }

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

    @Override
    public boolean isEmpty() {
        return values.isEmpty();
    }

    @Override
    public Map<String, Object> asMap() {
        return normalizeMap(values);
    }

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
