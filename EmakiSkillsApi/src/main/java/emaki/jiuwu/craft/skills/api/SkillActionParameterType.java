package emaki.jiuwu.craft.skills.api;

import java.util.Locale;

/** Supported parameter types for a skill-script action. */
public enum SkillActionParameterType {
    STRING,
    INTEGER,
    DOUBLE,
    BOOLEAN,
    TIME;

    public boolean isValid(String raw) {
        if (this == STRING) {
            return true;
        }
        if (isBlank(raw)) {
            return false;
        }
        String trimmed = raw.trim();
        try {
            return switch (this) {
                case INTEGER -> parseInt(trimmed);
                case DOUBLE -> parseDouble(trimmed);
                case BOOLEAN -> parseBoolean(trimmed);
                case TIME -> parseTicks(trimmed) >= 0L;
                case STRING -> true;
            };
        } catch (Exception _) {
            return false;
        }
    }

    private static boolean parseInt(String raw) {
        Integer.parseInt(raw);
        return true;
    }

    private static boolean parseDouble(String raw) {
        Double.parseDouble(raw);
        return true;
    }

    private static boolean parseBoolean(String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return "true".equals(normalized)
                || "false".equals(normalized)
                || "yes".equals(normalized)
                || "no".equals(normalized)
                || "1".equals(normalized)
                || "0".equals(normalized);
    }

    private static long parseTicks(String raw) {
        if (isBlank(raw)) {
            return -1L;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        try {
            if (trimmed.endsWith("ms")) {
                return Math.max(0L, Math.round(Double.parseDouble(trimmed.substring(0, trimmed.length() - 2)) / 50D));
            }
            if (trimmed.endsWith("s")) {
                return Math.max(0L, Math.round(Double.parseDouble(trimmed.substring(0, trimmed.length() - 1)) * 20D));
            }
            if (trimmed.endsWith("t")) {
                return Math.max(0L, Math.round(Double.parseDouble(trimmed.substring(0, trimmed.length() - 1))));
            }
            return Math.max(0L, Math.round(Double.parseDouble(trimmed)));
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }

    private static boolean isBlank(String raw) {
        return raw == null || raw.trim().isEmpty();
    }
}
