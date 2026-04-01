package emaki.jiuwu.craft.attribute.model;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.text.Texts;

public record AttributeSemanticDefinition(String id,
        String group,
        String role,
        String summary,
        double weight) {

    public AttributeSemanticDefinition     {
        id = normalizeId(id);
        group = Texts.toStringSafe(group).trim().toLowerCase(Locale.ROOT);
        role = Texts.toStringSafe(role).trim().toLowerCase(Locale.ROOT);
        summary = Texts.toStringSafe(summary).trim();
        weight = Double.isNaN(weight) ? 1D : weight;
    }

    private static String normalizeId(String value) {
        return Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
