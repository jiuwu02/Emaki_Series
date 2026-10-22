package emaki.jiuwu.craft.corelib.api.yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * {@link YamlSection} that wraps a live BoostedYAML section and reads and writes straight
 * through to it.
 *
 * <p>Unlike {@link MapYamlSection}, this view is not a snapshot: {@link #set} changes the
 * backing document immediately, and nested reads surface as further live wrappers. A wrapper
 * built around {@code null} is legal and answers every read with the given default (or
 * {@code null}/empty). Use {@link #copy()} when an independent snapshot is needed.
 */
public final class BoostedYamlSection implements YamlSection {

    private final Section section;

    /**
     * Wraps a BoostedYAML section.
     *
     * @param section section to wrap; {@code null} yields a wrapper whose reads always answer
     *                the default
     */
    public BoostedYamlSection(Section section) {
        this.section = section;
    }

    /**
     * {@return the wrapped BoostedYAML section, or {@code null} when this wrapper holds none}
     */
    public Section delegate() {
        return section;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Nested sections surface as live {@link BoostedYamlSection} wrappers; other structures
     * are unwrapped to plain maps and lists. A wrapper around {@code null} answers {@code null}.
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>A blank path or a wrapper around {@code null} always answers {@code false}.
     */
    @Override
    public boolean contains(String path) {
        return section != null && Texts.isNotBlank(path) && section.contains(path);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A wrapper around {@code null} answers {@code defaultValue} directly.
     */
    @Override
    public String getString(String path, String defaultValue) {
        return section == null ? defaultValue : section.getString(path, defaultValue);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Conversion delegates to the wrapped BoostedYAML section with the same default-on-failure
     * rule; a wrapper around {@code null} answers {@code defaultValue}.
     */
    @Override
    public Integer getInt(String path, Integer defaultValue) {
        return section == null ? defaultValue : section.getInt(path, defaultValue);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Conversion delegates to the wrapped BoostedYAML section with the same default-on-failure
     * rule; a wrapper around {@code null} answers {@code defaultValue}.
     */
    @Override
    public Double getDouble(String path, Double defaultValue) {
        return section == null ? defaultValue : section.getDouble(path, defaultValue);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Conversion delegates to the wrapped BoostedYAML section with the same default-on-failure
     * rule; a wrapper around {@code null} answers {@code defaultValue}.
     */
    @Override
    public Boolean getBoolean(String path, Boolean defaultValue) {
        return section == null ? defaultValue : section.getBoolean(path, defaultValue);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Elements are unwrapped to plain data before copying; a wrapper around {@code null}
     * answers the (copied) default or an empty list.
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>A wrapper around {@code null} answers an empty list.
     */
    @Override
    public List<String> getStringList(String path) {
        return section == null ? List.of() : List.copyOf(section.getStringList(path));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Each element map is copied into a fresh {@link LinkedHashMap} with normalized keys and
     * values.
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>The result is another live wrapper over the same document, not a snapshot; a blank path
     * returns this wrapper, and a wrapper around {@code null} answers {@code null}.
     */
    @Override
    public YamlSection getSection(String path) {
        if (section == null) {
            return null;
        }
        Section nested = Texts.isBlank(path) ? section : section.getSection(path, null);
        return nested == null ? null : new BoostedYamlSection(nested);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Shallow mode stringifies the direct keys of the wrapped section, skipping null keys;
     * deep mode lists the wrapped section's full routes.
     */
    @Override
    public Set<String> getKeys(boolean deep) {
        if (section == null) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        if (!deep) {
            for (Object key : section.getKeys()) {
                if (key != null) {
                    keys.add(String.valueOf(key));
                }
            }
        } else {
            keys.addAll(section.getRoutesAsStrings(true));
        }
        return Collections.unmodifiableSet(keys);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A blank path or a wrapper around {@code null} always answers {@code false}.
     */
    @Override
    public boolean isString(String path) {
        return section != null && Texts.isNotBlank(path) && section.isString(path);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Decided through {@link #getSection}: a wrapper around {@code null} answers
     * {@code false}.
     */
    @Override
    public boolean isSection(String path) {
        return getSection(path) != null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The write goes straight into the wrapped document; a blank path or a wrapper around
     * {@code null} is a no-op, and a {@code null} value removes the entry.
     */
    @Override
    public void set(String path, Object value) {
        if (section == null || Texts.isBlank(path)) {
            return;
        }
        if (value == null) {
            section.remove(path);
            return;
        }
        section.set(path, MapYamlSection.normalizeValue(value));
    }

    /**
     * {@inheritDoc}
     *
     * <p>A wrapper around {@code null} counts as empty; otherwise the result reflects the
     * wrapped section's own emptiness check.
     */
    @Override
    public boolean isEmpty() {
        return section == null || section.isEmpty(true);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The snapshot is built by walking the wrapped section's direct keys; nested sections
     * become plain maps.
     */
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

    /**
     * {@inheritDoc}
     *
     * <p>The snapshot materializes as a {@link MapYamlSection}: it no longer reads through to
     * the backing document.
     */
    @Override
    public YamlSection copy() {
        return new MapYamlSection(asMap());
    }

    /**
     * Unwraps one raw value read from a BoostedYAML section: nested sections become live
     * wrappers, other structures become plain maps and lists.
     */
    static Object unwrapBoostedValue(Object value) {
        if (value instanceof Section nestedSection) {
            return new BoostedYamlSection(nestedSection);
        }
        return MapYamlSection.unwrapValue(value);
    }
}
