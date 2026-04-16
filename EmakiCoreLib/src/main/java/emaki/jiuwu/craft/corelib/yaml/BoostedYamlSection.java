package emaki.jiuwu.craft.corelib.yaml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class BoostedYamlSection implements YamlSection {

    private final Section section;

    public BoostedYamlSection(Section section) {
        this.section = section;
    }

    public Section delegate() {
        return section;
    }

    @Override
    public Object get(String path) {
        if (section == null) {
            return null;
        }
        if (Texts.isBlank(path)) {
            return this;
        }
        Object raw = section.get(path);
        if (raw instanceof Section nestedSection) {
            return new BoostedYamlSection(nestedSection);
        }
        return MapYamlSection.unwrapValue(raw);
    }

    @Override
    public boolean contains(String path) {
        return section != null && Texts.isNotBlank(path) && section.contains(path);
    }

    @Override
    public String getString(String path, String defaultValue) {
        return section == null ? defaultValue : section.getString(path, defaultValue);
    }

    @Override
    public Integer getInt(String path, Integer defaultValue) {
        return section == null ? defaultValue : section.getInt(path, defaultValue);
    }

    @Override
    public Double getDouble(String path, Double defaultValue) {
        return section == null ? defaultValue : section.getDouble(path, defaultValue);
    }

    @Override
    public Boolean getBoolean(String path, Boolean defaultValue) {
        return section == null ? defaultValue : section.getBoolean(path, defaultValue);
    }

    @Override
    public List<?> getList(String path, List<?> defaultValue) {
        if (section == null) {
            return defaultValue == null ? List.of() : List.copyOf(defaultValue);
        }
        List<?> list = section.getList(path, defaultValue);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<Object> normalized = new ArrayList<>(list.size());
        for (Object value : list) {
            normalized.add(unwrapBoostedValue(value));
        }
        return List.copyOf(normalized);
    }

    @Override
    public List<String> getStringList(String path) {
        return section == null ? List.of() : List.copyOf(section.getStringList(path));
    }

    @Override
    public List<Map<?, ?>> getMapList(String path) {
        if (section == null) {
            return List.of();
        }
        List<Map<?, ?>> result = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList(path)) {
            result.add(new LinkedHashMap<>(MapYamlSection.normalizeMap(map)));
        }
        return List.copyOf(result);
    }

    @Override
    public YamlSection getSection(String path) {
        if (section == null) {
            return null;
        }
        Section nested = Texts.isBlank(path) ? section : section.getSection(path, null);
        return nested == null ? null : new BoostedYamlSection(nested);
    }

    @Override
    public Set<String> getKeys(boolean deep) {
        if (section == null) {
            return Set.of();
        }
        if (!deep) {
            Set<String> keys = new LinkedHashSet<>();
            for (Object key : section.getKeys()) {
                if (key != null) {
                    keys.add(String.valueOf(key));
                }
            }
            return Set.copyOf(keys);
        }
        return Set.copyOf(section.getRoutesAsStrings(true));
    }

    @Override
    public boolean isString(String path) {
        return section != null && Texts.isNotBlank(path) && section.isString(path);
    }

    @Override
    public boolean isSection(String path) {
        return getSection(path) != null;
    }

    @Override
    public void set(String path, Object value) {
        if (section == null || Texts.isBlank(path)) {
            return;
        }
        section.set(path, MapYamlSection.normalizeValue(value));
    }

    @Override
    public boolean isEmpty() {
        return section == null || section.isEmpty(true);
    }

    @Override
    public Map<String, Object> asMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (Object key : section.getKeys()) {
            if (key == null) {
                continue;
            }
            String textKey = String.valueOf(key);
            YamlSection nested = getSection(textKey);
            if (nested != null) {
                result.put(textKey, nested.asMap());
                continue;
            }
            result.put(textKey, MapYamlSection.normalizeValue(section.get(textKey)));
        }
        return result;
    }

    @Override
    public YamlSection copy() {
        return new MapYamlSection(asMap());
    }

    static Object unwrapBoostedValue(Object value) {
        if (value instanceof Section nestedSection) {
            return new BoostedYamlSection(nestedSection);
        }
        return MapYamlSection.unwrapValue(value);
    }
}
