package emaki.jiuwu.craft.corelib.api.assembly;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * One ordered block of lore lines contributed by a namespace layer to an assembled item's lore.
 *
 * <p>The compact constructor normalizes the components: a blank {@code sectionId} becomes
 * {@code unknown}, {@code order} is clamped to {@code >= 0}, {@code lines} becomes an immutable
 * empty list when {@code null} or empty and is copied unmodifiable otherwise, and
 * {@code sourceNamespace} is stringified ({@code null} becomes an empty string). Empty sections
 * are dropped when a presentation is assembled from configuration.
 *
 * @param sectionId stable identifier of the lore section
 * @param order ascending sort key controlling the section's position in the lore
 * @param lines the contributed lore lines; never {@code null} after construction
 * @param sourceNamespace namespace that produced the lines
 */
public record EmakiLoreSectionContribution(String sectionId,
        int order,
        List<String> lines,
        String sourceNamespace) {

    /** Normalizes the raw components as described in the type documentation. */
    public EmakiLoreSectionContribution {
        sectionId = Texts.isBlank(sectionId) ? "unknown" : Texts.toStringSafe(sectionId);
        order = Math.max(0, order);
        lines = lines == null || lines.isEmpty() ? List.of() : List.copyOf(lines);
        sourceNamespace = Texts.toStringSafe(sourceNamespace);
    }

    /**
     * {@return {@code true} when the section carries no lines}
     */
    public boolean isEmpty() {
        return lines == null || lines.isEmpty();
    }

    /**
     * {@return the section as a plain map with the {@code section_id}, {@code order},
     * {@code lines} and {@code source_namespace} keys}
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("section_id", sectionId);
        map.put("order", order);
        map.put("lines", lines);
        map.put("source_namespace", sourceNamespace);
        return map;
    }

    /**
     * Reads a section back from its {@link #toMap()} form.
     *
     * @param map plain map; {@code null} and empty are accepted
     * @return the parsed section, or {@code null} when the map is {@code null}, empty, or carries
     *         a blank {@code section_id}; a missing or unparsable {@code order} falls back to
     *         {@code 0} and a missing {@code lines} to an empty list
     */
    public static EmakiLoreSectionContribution fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String sectionId = Texts.toStringSafe(map.get("section_id"));
        if (Texts.isBlank(sectionId)) {
            return null;
        }
        return new EmakiLoreSectionContribution(
                sectionId,
                Numbers.tryParseInt(map.get("order"), 0),
                Texts.asStringList(ConfigNodes.toPlainData(map.get("lines"))),
                Texts.toStringSafe(map.get("source_namespace"))
        );
    }
}
