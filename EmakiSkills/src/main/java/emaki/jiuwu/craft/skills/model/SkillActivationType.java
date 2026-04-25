package emaki.jiuwu.craft.skills.model;

import emaki.jiuwu.craft.corelib.text.Texts;

public enum SkillActivationType {
    ACTIVE,
    PASSIVE;

    public static SkillActivationType fromString(String value) {
        String normalized = Texts.lower(value).replace('-', '_');
        if ("passive".equals(normalized)) {
            return PASSIVE;
        }
        return ACTIVE;
    }
}
