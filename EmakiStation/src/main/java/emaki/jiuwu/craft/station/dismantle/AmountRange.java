package emaki.jiuwu.craft.station.dismantle;

public record AmountRange(int min, int max) {

    public AmountRange {
        if (min < 1) {
            min = 1;
        }
        if (max < min) {
            max = min;
        }
    }

    public static AmountRange one() {
        return new AmountRange(1, 1);
    }

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

            }
        }
        return fallback;
    }
}
