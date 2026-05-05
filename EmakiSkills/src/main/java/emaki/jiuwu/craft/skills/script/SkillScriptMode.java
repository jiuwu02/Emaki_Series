package emaki.jiuwu.craft.skills.script;

import emaki.jiuwu.craft.corelib.text.Texts;

public enum SkillScriptMode {
    NATIVE,
    MYTHIC,
    HYBRID;

    public static SkillScriptMode fromString(String value, SkillScriptMode fallback) {
        String normalized = Texts.lower(value).replace('-', '_').trim();
        return switch (normalized) {
            case "native" -> NATIVE;
            case "mythic", "mythicmobs" -> MYTHIC;
            case "hybrid", "both" -> HYBRID;
            default -> fallback == null ? NATIVE : fallback;
        };
    }
}
