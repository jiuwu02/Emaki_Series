package emaki.jiuwu.craft.station.dismantle;

/**
 * An inclusive integer range used to roll how many outputs a single dismantle produces.
 *
 * @param min the minimum number of rolls (≥ 1)
 * @param max the maximum number of rolls (≥ min)
 */
public record RollsRange(int min, int max) {

    /**
     * Creates a validated range.
     *
     * @throws IllegalArgumentException when {@code min < 1} or {@code max < min}
     */
    public RollsRange {
        if (min < 1) {
            min = 1;
        }
        if (max < min) {
            max = min;
        }
    }

    /** {@return a fixed range of exactly one roll} */
    public static RollsRange one() {
        return new RollsRange(1, 1);
    }

    /**
     * Creates a range from raw configuration values.
     *
     * <p>If either value is missing (null) or invalid the corresponding bound falls back to {@code 1}.
     *
     * @param rawMin the configured minimum, may be {@code null}
     * @param rawMax the configured maximum, may be {@code null}
     * @return the parsed range
     */
    public static RollsRange of(Object rawMin, Object rawMax) {
        int min = parsePositiveInt(rawMin, 1);
        int max = parsePositiveInt(rawMax, min);
        return new RollsRange(min, max);
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
