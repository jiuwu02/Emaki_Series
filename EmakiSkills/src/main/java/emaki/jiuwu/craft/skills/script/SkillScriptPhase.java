package emaki.jiuwu.craft.skills.script;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public enum SkillScriptPhase {
    CAST,
    HIT,
    MISS,
    FAIL;

    public String configKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SkillScriptPhase fromString(String value) {
        String normalized = Texts.lower(value).replace('-', '_').trim();
        return switch (normalized) {
            case "cast" -> CAST;
            case "hit" -> HIT;
            case "miss" -> MISS;
            case "fail" -> FAIL;
            default -> null;
        };
    }
}
