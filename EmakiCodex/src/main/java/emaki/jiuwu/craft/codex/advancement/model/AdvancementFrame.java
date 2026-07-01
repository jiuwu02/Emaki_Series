package emaki.jiuwu.craft.codex.advancement.model;

import java.util.Locale;

/**
 * Vanilla advancement frame styles that control the icon border and completion
 * announcement wording shown by the client.
 */
public enum AdvancementFrame {

    TASK,
    GOAL,
    CHALLENGE;

    /** {@return the lowercase token written into advancement JSON} */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Resolves a frame from a configured string, defaulting to {@link #TASK}.
     *
     * @param raw the configured value (case-insensitive)
     * @return the matching frame, or {@link #TASK} when blank/unknown
     */
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
