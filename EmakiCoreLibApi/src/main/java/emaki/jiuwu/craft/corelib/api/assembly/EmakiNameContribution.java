package emaki.jiuwu.craft.corelib.api.assembly;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * One templated name fragment contributed by a namespace layer and rendered either before or
 * after the item's base display name.
 *
 * <p>The compact constructor normalizes the components: a blank {@code slotId} becomes
 * {@code unknown}, a {@code null} {@code position} becomes {@link NamePosition#POSTFIX},
 * {@code order} is clamped to {@code >= 0}, and {@code contentTemplate} and {@code sourceNamespace}
 * are stringified ({@code null} becomes an empty string). Within one position the renderer sorts
 * contributions ascending by {@code order}.
 *
 * @param slotId stable identifier of the contributing slot
 * @param position whether the fragment renders before or after the base name
 * @param order ascending sort key within the position group
 * @param contentTemplate MiniMessage template of the fragment; may reference stat placeholders
 * @param sourceNamespace namespace that produced the fragment
 */
public record EmakiNameContribution(String slotId,
        NamePosition position,
        int order,
        String contentTemplate,
        String sourceNamespace) {

    /** Normalizes the raw components as described in the type documentation. */
    public EmakiNameContribution {
        slotId = Texts.isBlank(slotId) ? "unknown" : Texts.toStringSafe(slotId);
        position = position == null ? NamePosition.POSTFIX : position;
        order = Math.max(0, order);
        contentTemplate = Texts.toStringSafe(contentTemplate);
        sourceNamespace = Texts.toStringSafe(sourceNamespace);
    }

    /**
     * {@return the contribution as a plain map with the {@code slot_id}, {@code position},
     * {@code order}, {@code content_template} and {@code source_namespace} keys}
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("slot_id", slotId);
        map.put("position", position.name());
        map.put("order", order);
        map.put("content_template", contentTemplate);
        map.put("source_namespace", sourceNamespace);
        return map;
    }

    /**
     * Reads a contribution back from its {@link #toMap()} form.
     *
     * @param map plain map; {@code null} and empty are accepted
     * @return the parsed contribution, or {@code null} when the map is {@code null}, empty, or
     *         carries a blank {@code slot_id}; a missing or unrecognized {@code position} falls
     *         back to {@link NamePosition#POSTFIX} and a missing {@code order} to {@code 0}
     */
    public static EmakiNameContribution fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String slotId = Texts.toStringSafe(map.get("slot_id"));
        if (Texts.isBlank(slotId)) {
            return null;
        }
        return new EmakiNameContribution(
                slotId,
                NamePosition.fromValue(map.get("position")),
                Numbers.tryParseInt(map.get("order"), 0),
                Texts.toStringSafe(map.get("content_template")),
                Texts.toStringSafe(map.get("source_namespace"))
        );
    }
}
