package emaki.jiuwu.craft.corelib.yaml;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface YamlSection {

    Object get(String path);

    boolean contains(String path);

    String getString(String path, String defaultValue);

    default String getString(String path) {
        return getString(path, null);
    }

    Integer getInt(String path, Integer defaultValue);

    default Integer getInt(String path) {
        return getInt(path, null);
    }

    Double getDouble(String path, Double defaultValue);

    default Double getDouble(String path) {
        return getDouble(path, null);
    }

    Boolean getBoolean(String path, Boolean defaultValue);

    default Boolean getBoolean(String path) {
        return getBoolean(path, null);
    }

    List<?> getList(String path, List<?> defaultValue);

    default List<?> getList(String path) {
        return getList(path, List.of());
    }

    List<String> getStringList(String path);

    List<Map<?, ?>> getMapList(String path);

    YamlSection getSection(String path);

    Set<String> getKeys(boolean deep);

    boolean isString(String path);

    boolean isSection(String path);

    void set(String path, Object value);

    boolean isEmpty();

    Map<String, Object> asMap();

    YamlSection copy();
}
