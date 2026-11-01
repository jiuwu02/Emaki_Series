package emaki.jiuwu.craft.corelib.api.assembly;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Registration entry for one presentation namespace (layer) that may contribute names, lore or
 * stats to assembled item displays, such as {@code forge} or {@code gem}.
 *
 * <p>Components are normalized by the compact constructor: {@code id} is trimmed, lowercased with
 * {@link Locale#ROOT} and has spaces replaced by {@code _} (falling back to {@code unknown} when
 * blank), and a blank {@code displayName} falls back to the normalized {@code id}. The registry
 * sorts definitions ascending by {@code order} and breaks ties by normalized {@code id}.
 *
 * @param id stable namespace identifier
 * @param order ascending sort key controlling the layer's position in the stack
 * @param displayName human-readable namespace name shown in debug output
 */
public record EmakiNamespaceDefinition(String id, int order, String displayName) {

    /** Normalizes the raw components as described in the type documentation. */
    public EmakiNamespaceDefinition   {
        id = normalizeId(id);
        displayName = Texts.isBlank(displayName) ? id : displayName;
    }

    private static String normalizeId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return normalized.isBlank() ? "unknown" : normalized;
    }
}
