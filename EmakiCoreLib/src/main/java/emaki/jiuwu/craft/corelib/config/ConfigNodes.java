package emaki.jiuwu.craft.corelib.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public final class ConfigNodes {

    private ConfigNodes() {
    }

    public static Object get(Object mapping, String key) {
        if (mapping == null || key == null) {
            return null;
        }
        if (mapping instanceof YamlSection section) {
            return section.get(key);
        }
        if (mapping instanceof Map<?, ?> map) {
            return map.get(key);
        }
        return null;
    }

    public static boolean contains(Object mapping, String key) {
        if (mapping == null || key == null) {
            return false;
        }
        if (mapping instanceof YamlSection section) {
            return section.contains(key);
        }
        if (mapping instanceof Map<?, ?> map) {
            return map.containsKey(key);
        }
        return false;
    }

    public static Map<String, Object> entries(Object mapping) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (mapping == null) {
            return result;
        }
        if (mapping instanceof YamlSection section) {
            Set<String> keys = section.getKeys(false);
            for (String key : keys) {
                result.put(key, section.get(key));
            }
            return result;
        }
        if (mapping instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    public static Object toPlainData(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean) {
            return value;
        }
        if (value instanceof YamlSection section) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                result.put(key, toPlainData(section.get(key)));
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                result.put(String.valueOf(entry.getKey()), toPlainData(entry.getValue()));
            }
            return result;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>();
            for (Object entry : collection) {
                result.add(toPlainData(entry));
            }
            return result;
        }
        return value;
    }

    public static String string(Object mapping, String key, String defaultValue) {
        Object value = get(mapping, key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    public static boolean bool(Object mapping, String key, boolean defaultValue) {
        Object value = get(mapping, key);
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public static YamlSection section(Object mapping, String key) {
        if (mapping instanceof YamlSection section) {
            return section.getSection(key);
        }
        Object value = get(mapping, key);
        if (value instanceof Map<?, ?> map) {
            return new MapYamlSection(MapYamlSection.normalizeMap(map));
        }
        return value instanceof YamlSection section ? section : null;
    }

    public static List<Object> asObjectList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return List.of(value);
    }
}
