package emaki.jiuwu.craft.station.dismantle;

/**
 * An inclusive integer range used to determine how many of a specific item is given per roll.
 *
 * @param min the minimum amount (≥ 1)
 * @param max the maximum amount (≥ min)
 */
public record AmountRange(int min, int max) {

    /**
     * Creates a validated range.
     */
    public AmountRange {
        if (min < 1) {
            min = 1;
        }
        if (max < min) {
            max = min;
        }
    }

    /** {@return a fixed amount of exactly one} */
    public static AmountRange one() {
        return new AmountRange(1, 1);
    }

    /**
     * Creates a range from raw configuration values.
     *
     * @param rawMin the configured minimum, may be {@code null}
     * @param rawMax the configured maximum, may be {@code null}
     * @return the parsed range
     */
    public static AmountRange of(Object rawMin, Object rawMax) {
        int min = parsePositiveInt(rawMin, 1);
        int max = parsePositiveInt(rawMax, min);
        return new AmountRange(min, max);
    }

    private static int parsePositiveInt(Object raw, int fallback) {
        if (raw instanceof Number n) {
            int v = n.intValue();
            return v >= 1 ? v : fallback;
        }
        if (raw instanceof String s) {
            try {
                int v = Integer.parseInt(s.trim());
                return v >= 1 ? v : fallback;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return fallback;
    }
}
