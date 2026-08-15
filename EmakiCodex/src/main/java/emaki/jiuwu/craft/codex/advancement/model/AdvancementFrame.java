package emaki.jiuwu.craft.codex.advancement.model;

import java.util.Locale;

public enum AdvancementFrame {

    TASK,
    GOAL,
    CHALLENGE;

    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AdvancementFrame fromText(String raw) {
        if (raw == null || raw.isBlank()) {
            return TASK;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return TASK;
        }
    }
}
