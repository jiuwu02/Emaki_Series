package emaki.jiuwu.craft.corelib.action.pipeline.compile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

import org.bukkit.Particle;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Scalar parsing shared by the pipeline compiler, the runtime and every stage.
 *
 * <p>This is the former {@code action.ActionParsers}, moved here unchanged when the v1 action package was
 * removed. It never depended on the v1 action types: it only turns configured text into numbers,
 * durations, chances and particles, which is why it outlived the system it was named after.</p>
 *
 * <p>Behaviour is deliberately identical to the v1 version, including its return conventions
 * ({@code -1} for an unparsable duration or chance, {@code null} for an unknown particle). Server owners'
 * configuration is parsed by these rules, so tightening them here would change how existing values are
 * read.</p>
 */
public final class ValueParsers {

    /**
     * Denominator used to turn a chance into an integer threshold.
     *
     * <p>A billion, so that a comparison against a random long has enough resolution to represent the
     * small probabilities configuration uses without floating-point drift.</p>
     */
    private static final long CHANCE_DENOMINATOR = 1_000_000_000L;

    private ValueParsers() {
    }

    /**
     * Parses an integer.
     *
     * @param raw the text
     * @param fallback value used when the text is blank or not a number
     * @return the parsed value
     */
    public static int parseInt(String raw, int fallback) {
        if (Texts.isBlank(raw)) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Parses an integer, distinguishing absent from invalid.
     *
     * @param raw the text
     * @return the parsed value, or {@code null} when blank or not a number
     */
    public static Integer parseIntNullable(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses a double.
     *
     * @param raw the text
     * @param fallback value used when the text is blank or not a number
     * @return the parsed value
     */
    public static double parseDouble(String raw, double fallback) {
        if (Texts.isBlank(raw)) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Parses a double, distinguishing absent from invalid.
     *
     * @param raw the text
     * @return the parsed value, or {@code null} when blank or not a number
     */
    public static Double parseDoubleNullable(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses a boolean through the expression engine, so {@code %var%>3} works where a flag is expected.
     *
     * @param raw the text
     * @return the parsed value, or {@code null} when it is not a boolean expression
     */
    public static Boolean parseBoolean(String raw) {
        return ExpressionEngine.evaluateBoolean(raw);
    }

    /**
     * Parses a duration into ticks, accepting {@code ms}, {@code s} and {@code t} suffixes.
     *
     * @param raw the text
     * @return ticks, or {@code -1} when unparsable
     */
    public static long parseTicks(String raw) {
        if (Texts.isBlank(raw)) {
            return -1L;
        }
        String trimmed = Texts.trim(raw).toLowerCase(Locale.ROOT);
        try {
            if (trimmed.endsWith("ms")) {
                return Math.max(0L, Math.round(
                        Double.parseDouble(trimmed.substring(0, trimmed.length() - 2)) / 50D));
            }
            if (trimmed.endsWith("s")) {
                return Math.max(0L, Math.round(
                        Double.parseDouble(trimmed.substring(0, trimmed.length() - 1)) * 20D));
            }
            if (trimmed.endsWith("t")) {
                return Math.max(0L, Math.round(
                        Double.parseDouble(trimmed.substring(0, trimmed.length() - 1))));
            }
            return Math.max(0L, Math.round(Double.parseDouble(trimmed)));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * Parses a chance as a fraction, accepting {@code 50%} and {@code 0.5}.
     *
     * @param raw the text
     * @return the fraction, or {@code -1} when unparsable
     */
    public static double parseChance(String raw) {
        BigDecimal parsed = parseChanceDecimal(raw);
        return parsed == null ? -1D : parsed.doubleValue();
    }

    /**
     * Parses a chance into an integer threshold over {@link #chanceDenominator()}.
     *
     * @param raw the text
     * @return the threshold, or {@code -1} when unparsable or outside {@code 0..1}
     */
    public static long parseChanceThreshold(String raw) {
        BigDecimal parsed = parseChanceDecimal(raw);
        if (parsed == null || parsed.compareTo(BigDecimal.ZERO) < 0
                || parsed.compareTo(BigDecimal.ONE) > 0) {
            return -1L;
        }
        return parsed.multiply(BigDecimal.valueOf(CHANCE_DENOMINATOR))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** {@return the denominator {@link #parseChanceThreshold} scales to} */
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
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    /**
     * Removes a leading slash from a command.
     *
     * @param command the command text
     * @return the command without its leading slash
     */
    public static String stripLeadingSlash(String command) {
        String trimmed = Texts.trim(command);
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    /**
     * Parses a particle name, tolerating the {@code minecraft:} prefix and dotted keys.
     *
     * @param raw the text
     * @return the particle, or {@code null} when unknown
     */
    public static Particle parseParticle(String raw) {
        if (Texts.isBlank(raw)) {
            return null;
        }
        String normalized = Texts.trim(raw)
                .replace("minecraft:", "")
                .replace('.', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return Particle.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parses a coordinate, supporting {@code ~} and {@code ~offset} relative forms.
     *
     * @param raw the text
     * @param base value {@code ~} resolves to
     * @return the coordinate
     */
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
