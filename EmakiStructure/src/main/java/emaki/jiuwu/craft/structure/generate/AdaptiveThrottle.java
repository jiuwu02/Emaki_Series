package emaki.jiuwu.craft.structure.generate;

public final class AdaptiveThrottle {

    private static final double FULL_TPS = 19.0d;
    private static final double FLOOR_TPS = 15.0d;
    private static final int FLOOR_DIVISOR = 4;

    private AdaptiveThrottle() {
    }

    public static int effectiveBudget(int baseBudget, boolean adaptive, double tps) {
        if (!adaptive) {
            return baseBudget;
        }
        if (tps >= FULL_TPS) {
            return baseBudget;
        }
        if (tps <= FLOOR_TPS) {
            return Math.max(1, baseBudget / FLOOR_DIVISOR);
        }
        double ratio = (tps - FLOOR_TPS) / (FULL_TPS - FLOOR_TPS);
        double factor = 1.0d / FLOOR_DIVISOR + (1.0d - 1.0d / FLOOR_DIVISOR) * ratio;
        int scaled = (int) Math.round(baseBudget * factor);
        return Math.max(1, Math.min(baseBudget, scaled));
    }

    public static boolean shouldWarn(boolean adaptive, double tps) {
        return adaptive && tps < FULL_TPS;
    }
}
