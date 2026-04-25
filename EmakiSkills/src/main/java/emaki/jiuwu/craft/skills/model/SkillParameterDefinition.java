package emaki.jiuwu.craft.skills.model;

import emaki.jiuwu.craft.corelib.text.Texts;

public record SkillParameterDefinition(
        String id,
        SkillParameterType type,
        String value,
        String formula,
        Double min,
        Double max,
        int decimals,
        String defaultValue
) {

    public SkillParameterDefinition {
        id = Texts.normalizeId(id);
        type = type == null ? SkillParameterType.NUMBER : type;
        value = Texts.toStringSafe(value);
        formula = Texts.toStringSafe(formula);
        decimals = Math.max(0, decimals);
        defaultValue = Texts.toStringSafe(defaultValue);
    }
}
