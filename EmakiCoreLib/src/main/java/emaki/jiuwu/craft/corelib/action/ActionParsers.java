package emaki.jiuwu.craft.corelib.action;

import org.bukkit.Particle;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ActionParsers {

    private ActionParsers() {
    }

    public static int parseInt(String raw, int fallback) {
        if (Texts.isBlank(raw)) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static Integer parseIntNullable(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static double parseDouble(String raw, double fallback) {
        if (Texts.isBlank(raw)) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static Double parseDoubleNullable(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static Boolean parseBoolean(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        return null;
    }

    public static long parseTicks(String raw) {
        if (Texts.isBlank(raw)) {
            return -1L;
        }
        String trimmed = Texts.trim(raw).toLowerCase();
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
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    public static double parseChance(String raw) {
        if (Texts.isBlank(raw)) {
            return -1D;
        }
        String trimmed = Texts.trim(raw);
        try {
            if (trimmed.endsWith("%")) {
                return Double.parseDouble(trimmed.substring(0, trimmed.length() - 1)) / 100D;
            }
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ignored) {
            return -1D;
        }
    }

    public static String stripLeadingSlash(String command) {
        String trimmed = Texts.trim(command);
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    public static Particle parseParticle(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        String normalized = Texts.trim(raw).replace("minecraft:", "").replace('.', '_').toUpperCase();
        try {
            return Particle.valueOf(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static double parseCoordinate(String raw, double base) {
        if (Texts.isBlank(raw)) {
            return base;
        }
        String trimmed = Texts.trim(raw);
        if ("~".equals(trimmed)) {
            return base;
        }
        if (trimmed.startsWith("~")) {
            return base + parseDouble(trimmed.substring(1), 0D);
        }
        return parseDouble(trimmed, base);
    }
}
