package emaki.jiuwu.craft.attribute.model;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.text.Texts;

public record AttributeSemanticDefinition(String id,
        String group,
        String role,
        String summary,
        double score) {

    public AttributeSemanticDefinition     {
        id = Texts.normalizeId(id);
        group = Texts.toStringSafe(group).trim().toLowerCase(Locale.ROOT);
        role = Texts.toStringSafe(role).trim().toLowerCase(Locale.ROOT);
        summary = Texts.toStringSafe(summary).trim();
        score = Double.isNaN(score) ? 1D : score;
    }
}

