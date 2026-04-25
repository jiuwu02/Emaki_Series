package emaki.jiuwu.craft.skills.model;

import emaki.jiuwu.craft.corelib.text.Texts;

public enum SkillParameterType {
    NUMBER,
    STRING,
    BOOLEAN;

    public static SkillParameterType fromString(String value) {
        return switch (Texts.lower(value).replace('-', '_')) {
            case "string", "text" -> STRING;
            case "boolean", "bool" -> BOOLEAN;
            default -> NUMBER;
        };
    }
}
