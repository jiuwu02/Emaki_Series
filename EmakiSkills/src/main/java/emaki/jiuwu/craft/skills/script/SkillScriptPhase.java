package emaki.jiuwu.craft.skills.script;

import emaki.jiuwu.craft.corelib.text.Texts;

public enum SkillScriptPhase {
    CAST,
    HIT,
    MISS,
    FAIL;

    public String configKey() {
        return name().toLowerCase();
    }

    public static SkillScriptPhase fromString(String value) {
        String normalized = Texts.lower(value).replace('-', '_').trim();
        return switch (normalized) {
            case "cast", "on_cast" -> CAST;
            case "hit", "on_hit" -> HIT;
            case "miss", "on_miss" -> MISS;
            case "fail", "on_fail" -> FAIL;
            default -> null;
        };
    }
}
