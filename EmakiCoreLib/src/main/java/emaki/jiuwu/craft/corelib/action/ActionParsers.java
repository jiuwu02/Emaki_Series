package emaki.jiuwu.craft.corelib.action;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.bukkit.Particle;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ActionParsers {

    private static final long CHANCE_DENOMINATOR = 1_000_000_000L;

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
        BigDecimal parsed = parseChanceDecimal(raw);
        return parsed == null ? -1D : parsed.doubleValue();
    }

    public static long parseChanceThreshold(String raw) {
        BigDecimal parsed = parseChanceDecimal(raw);
        if (parsed == null || parsed.compareTo(BigDecimal.ZERO) < 0 || parsed.compareTo(BigDecimal.ONE) > 0) {
            return -1L;
        }
        return parsed.multiply(BigDecimal.valueOf(CHANCE_DENOMINATOR))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public static long chanceDenominator() {
        return CHANCE_DENOMINATOR;
    }

    private static BigDecimal parseChanceDecimal(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        String trimmed = Texts.trim(raw);
        try {
            if (trimmed.endsWith("%")) {
                return new BigDecimal(trimmed.substring(0, trimmed.length() - 1))
                        .divide(BigDecimal.valueOf(100L), 12, RoundingMode.HALF_UP);
            }
            return new BigDecimal(trimmed);
        } catch (NumberFormatException | ArithmeticException ignored) {
            return null;
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
