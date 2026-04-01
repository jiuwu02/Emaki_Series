package emaki.jiuwu.craft.corelib.assembly;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.text.Texts;

public record EmakiNamespaceDefinition(String id, int order, String displayName) {

    public EmakiNamespaceDefinition   {
        id = normalizeId(id);
        displayName = Texts.isBlank(displayName) ? id : displayName;
    }

    private static String normalizeId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return normalized.isBlank() ? "unknown" : normalized;
    }
}
