package emaki.jiuwu.craft.corelib.api.yaml;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read and write view over a YAML document addressed by dot-separated paths ({@code "a.b.c"}).
 *
 * <p>Two implementations ship with CoreLib: {@link BoostedYamlSection} wraps a live BoostedYAML
 * section and writes through to it, while {@link MapYamlSection} works on a plain map snapshot.
 * Both are tolerant on every read: a missing path or a wrapped {@code null} answers the given
 * default (or {@code null}/empty) instead of throwing, and returned collections are immutable.
 * Nested nodes returned by {@link #get} may surface either as a {@link YamlSection} or as a
 * plain {@link Map}; ask {@link #getSection} when a stable section view is needed.
 */
public interface YamlSection {

    /**
     * Reads the raw value at {@code path}.
     *
     * @param path dot-separated path; a blank path returns this section itself
     * @return the node's value (text, number, boolean, list, or a nested structure the
     *         implementation may surface as a {@link YamlSection} or a plain {@link Map}), or
     *         {@code null} when the path does not exist
     */
    Object get(String path);

    /**
     * Checks whether a node exists at {@code path}.
     *
     * @param path dot-separated path
     * @return {@code true} when every segment of the path resolves to a node
     */
    boolean contains(String path);

    /**
     * Reads the node at {@code path} as text.
     *
     * @param path dot-separated path
     * @param defaultValue returned when the path does not resolve to a value
     * @return the node's string form, or {@code defaultValue}
     */
    String getString(String path, String defaultValue);

    /**
     * Reads the node at {@code path} as text, defaulting to {@code null}.
     *
     * @param path dot-separated path
     * @return the node's string form, or {@code null} when the path does not exist
     */
    default String getString(String path) {
        return getString(path, null);
    }

    /**
     * Reads the node at {@code path} as an int.
     *
     * @param path dot-separated path
     * @param defaultValue returned when the node is missing or cannot be read as an integer
     * @return the value as an int, or {@code defaultValue}
     */
    Integer getInt(String path, Integer defaultValue);

    /**
     * Reads the node at {@code path} as an int, defaulting to {@code null}.
     *
     * @param path dot-separated path
     * @return the value as an int, or {@code null} when the node is missing or unreadable
     */
    default Integer getInt(String path) {
        return getInt(path, null);
    }

    /**
     * Reads the node at {@code path} as a double.
     *
     * @param path dot-separated path
     * @param defaultValue returned when the node is missing or cannot be read as a number
     * @return the value as a double, or {@code defaultValue}
     */
    Double getDouble(String path, Double defaultValue);

    /**
     * Reads the node at {@code path} as a double, defaulting to {@code null}.
     *
     * @param path dot-separated path
     * @return the value as a double, or {@code null} when the node is missing or unreadable
     */
    default Double getDouble(String path) {
        return getDouble(path, null);
    }

    /**
     * Reads the node at {@code path} as a boolean.
     *
     * @param path dot-separated path
     * @param defaultValue returned when the path does not resolve to a value
     * @return the node's boolean form, or {@code defaultValue}
     */
    Boolean getBoolean(String path, Boolean defaultValue);

    /**
     * Reads the node at {@code path} as a boolean, defaulting to {@code null}.
     *
     * @param path dot-separated path
     * @return the node's boolean form, or {@code null} when the path does not exist
     */
    default Boolean getBoolean(String path) {
        return getBoolean(path, null);
    }

    /**
     * Reads the node at {@code path} as a list.
     *
     * @param path dot-separated path
     * @param defaultValue returned (as a copy) when the path holds no list; {@code null} becomes
     *                      an empty list
     * @return an immutable list; never {@code null}
     */
    List<?> getList(String path, List<?> defaultValue);

    /**
     * Reads the node at {@code path} as a list, defaulting to empty.
     *
     * @param path dot-separated path
     * @return an immutable list; empty when the path holds no list
     */
    default List<?> getList(String path) {
        return getList(path, List.of());
    }

    /**
     * Reads the node at {@code path} as a list of strings.
     *
     * @param path dot-separated path
     * @return an immutable list of the elements' string forms; empty when the path holds no list
     */
    List<String> getStringList(String path);

    /**
     * Reads the node at {@code path} as a list of maps.
     *
     * @param path dot-separated path
     * @return an immutable list of the map elements (non-map elements skipped); empty when the
     *         path holds no list
     */
    List<Map<?, ?>> getMapList(String path);

    /**
     * Reads the node at {@code path} as a nested section view.
     *
     * @param path dot-separated path; a blank path returns this section itself
     * @return the nested section, or {@code null} when the path is missing or holds a value that
     *         is not a section
     */
    YamlSection getSection(String path);

    /**
     * Lists the keys of this section.
     *
     * @param deep when {@code true}, include the dotted routes of all nested keys instead of
     *             only the direct children
     * @return an immutable set of keys in document order; empty when the section has none
     */
    Set<String> getKeys(boolean deep);

    /**
     * Checks whether the node at {@code path} is a text value.
     *
     * @param path dot-separated path
     * @return {@code true} only for an existing text node
     */
    boolean isString(String path);

    /**
     * Checks whether the node at {@code path} is a nested section.
     *
     * @param path dot-separated path
     * @return {@code true} only for an existing section node
     */
    boolean isSection(String path);

    /**
     * Writes {@code value} at {@code path}, creating missing intermediate sections.
     *
     * @param path dot-separated path
     * @param value new value; {@code null} removes the node
     */
    void set(String path, Object value);

    /**
     * Checks whether the section has no content.
     *
     * @return {@code true} when the section holds no entries
     */
    boolean isEmpty();

    /**
     * Snapshots the section as plain data.
     *
     * @return an independent map with string keys and normalized nested maps and lists; never
     *         {@code null}
     */
    Map<String, Object> asMap();

    /**
     * Creates an independent snapshot of this section.
     *
     * @return a section backed by copied data, no longer reading through to the original
     *         document; never {@code null}
     */
    YamlSection copy();
}
