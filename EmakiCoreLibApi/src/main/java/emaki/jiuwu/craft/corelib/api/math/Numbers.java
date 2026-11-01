package emaki.jiuwu.craft.corelib.api.math;

import java.math.BigDecimal;
import java.text.DecimalFormat;

import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Lenient parsing, clamping and formatting helpers for values that arrive from configuration,
 * chat input or placeholders as {@code Object}.
 *
 * <p>All {@code tryParse*} methods treat {@code null} and unparsable input as "use the default"
 * instead of throwing, and trim the candidate string before parsing.
 */
public final class Numbers {

    private Numbers() {
    }

    /**
     * Reads an int from a number or an integer string.
     *
     * <p>A {@link Number} is narrowed with {@code intValue()} (fractional parts are truncated);
     * any other value must be an integer literal, so a string such as {@code "3.7"} falls back to
     * {@code defaultValue}.
     *
     * @param value value to read; {@code null} yields {@code defaultValue}
     * @param defaultValue value to return when the input is {@code null} or unparsable
     * @return the parsed value, or {@code defaultValue}
     */
    public static Integer tryParseInt(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Texts.trim(value));
        } catch (Exception _) {
            return defaultValue;
        }
    }

    /**
     * Reads a long from a number or an integer string.
     *
     * <p>A {@link Number} is narrowed with {@code longValue()} (fractional parts are truncated);
     * any other value must be an integer literal.
     *
     * @param value value to read; {@code null} yields {@code defaultValue}
     * @param defaultValue value to return when the input is {@code null} or unparsable
     * @return the parsed value, or {@code defaultValue}
     */
    public static Long tryParseLong(Object value, Long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(Texts.trim(value));
        } catch (Exception _) {
            return defaultValue;
        }
    }

    /**
     * Reads a double from a number or a numeric string.
     *
     * <p>The string form is trimmed first and is accepted whenever
     * {@link Double#parseDouble(String)} accepts it, which includes forms such as
     * {@code "1e3"}, {@code "1.5f"}, {@code "NaN"} and {@code "Infinity"}.
     *
     * @param value value to read; {@code null} yields {@code defaultValue}
     * @param defaultValue value to return when the input is {@code null} or unparsable
     * @return the parsed value, or {@code defaultValue}
     */
    public static Double tryParseDouble(Object value, Double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Texts.trim(value));
        } catch (Exception _) {
            return defaultValue;
        }
    }

    /**
     * Checks whether the value can be read as a double.
     *
     * <p>Every {@link Number} instance qualifies (even one holding {@code NaN}), and so does any
     * string {@link Double#parseDouble(String)} accepts; no range or finiteness check happens.
     *
     * @param value value to test; {@code null} yields {@code false}
     * @return {@code true} when {@link #tryParseDouble(Object, Double)} would return a value
     */
    public static boolean isNumeric(Object value) {
        return tryParseDouble(value, null) != null;
    }

    /**
     * Clamps an int into {@code [min, max]}.
     *
     * <p>When {@code min} is greater than {@code max}, the result is {@code min}.
     *
     * @param value value to clamp
     * @param min lower bound
     * @param max upper bound
     * @return {@code value} bounded by the two arguments
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps a double into {@code [min, max]}.
     *
     * <p>When {@code min} is greater than {@code max}, the result is {@code min}. A
     * {@code NaN} input or bound produces {@code NaN}; with normal bounds an infinite input
     * lands on the closest bound.
     *
     * @param value value to clamp
     * @param min lower bound
     * @param max upper bound
     * @return {@code value} bounded by the two arguments
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Renders a double without scientific notation.
     *
     * <p>Finite values go through {@link BigDecimal#toPlainString()} (so {@code 1.0E-7} becomes
     * {@code 0.0000001}), while {@code NaN} and the infinities are rendered with
     * {@link Double#toString(double)} because {@link BigDecimal} cannot represent them.
     *
     * @param value value to render
     * @return the plain decimal text
     */
    public static String toPlainString(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.toString(value);
        }
        return BigDecimal.valueOf(value).toPlainString();
    }

    /**
     * Formats a double with a {@link DecimalFormat} pattern.
     *
     * @param value value to format
     * @param pattern {@link DecimalFormat} pattern; blank falls back to {@code "0.##"} (up to two
     *                 decimals with trailing zeros dropped)
     * @return the formatted text, or {@link Double#toString(double)} when the pattern is invalid
     */
    public static String formatNumber(double value, String pattern) {
        String effectivePattern = Texts.isBlank(pattern) ? "0.##" : pattern;
        try {
            return new DecimalFormat(effectivePattern).format(value);
        } catch (Exception _) {
            return Double.toString(value);
        }
    }

    /**
     * Rounds a double to the nearest int, with halves rounding toward positive infinity.
     *
     * @param value value to round
     * @return the {@link Math#round(double)} result narrowed to {@code int}
     */
    public static int roundToInt(double value) {
        return (int) Math.round(value);
    }

    /**
     * Floors a double and narrows the result to int.
     *
     * @param value value to floor
     * @return the greatest int less than or equal to {@code value}
     */
    public static int floor(double value) {
        return (int) Math.floor(value);
    }

    /**
     * Ceils a double and narrows the result to int.
     *
     * @param value value to ceil
     * @return the least int greater than or equal to {@code value}
     */
    public static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    /**
     * Divides with a fallback for a zero denominator.
     *
     * <p>An exactly zero denominator (including {@code -0.0}) yields {@code defaultValue}, so
     * {@code 0 / 0} never surfaces as {@code NaN}; every other input follows IEEE 754 semantics.
     *
     * @param numerator dividend
     * @param denominator divisor
     * @param defaultValue returned when {@code denominator} is zero
     * @return the quotient, or {@code defaultValue} for a zero denominator
     */
    public static double safeDivide(double numerator, double denominator, double defaultValue) {
        if (denominator == 0D) {
            return defaultValue;
        }
        return numerator / denominator;
    }

    /**
     * Reads a percentage such as {@code "50%"} into its number.
     *
     * <p>One trailing {@code %} is stripped and the remainder is parsed as a double; the value is
     * not divided by 100, so {@code "50%"} yields {@code 50.0}. A bare number without the sign
     * parses the same way.
     *
     * @param value value to read; {@code null} yields {@code null}
     * @return the parsed percentage, or {@code null} when the input is {@code null}, blank or
     *         unparsable
     */
    public static Double parsePercentage(Object value) {
        if (value == null) {
            return null;
        }
        String text = Texts.trim(value);
        if (text.endsWith("%")) {
            text = text.substring(0, text.length() - 1);
        }
        return tryParseDouble(text, null);
    }
}
