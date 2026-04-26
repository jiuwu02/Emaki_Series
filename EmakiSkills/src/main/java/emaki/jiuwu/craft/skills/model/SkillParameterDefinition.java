package emaki.jiuwu.craft.skills.model;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;

public record SkillParameterDefinition(
        String id,
        SkillParameterType type,
        Object config,
        Double min,
        Double max,
        int decimals,
        String defaultValue
) {

    public SkillParameterDefinition {
        id = Texts.normalizeId(id);
        type = type == null ? SkillParameterType.CONSTANT : type;
        config = ConfigNodes.toPlainData(config);
        decimals = Math.max(0, decimals);
        defaultValue = Texts.toStringSafe(defaultValue);
    }
}
