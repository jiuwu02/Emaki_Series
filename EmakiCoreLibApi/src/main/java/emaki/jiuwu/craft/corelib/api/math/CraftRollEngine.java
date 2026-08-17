package emaki.jiuwu.craft.corelib.api.math;

import java.util.concurrent.ThreadLocalRandom;

public final class CraftRollEngine {

    private CraftRollEngine() {}

    public static boolean roll(double rate) {
        return ThreadLocalRandom.current().nextDouble(100D) < rate;
    }

    public static double clamp(double rate) {
        return Double.isFinite(rate) ? Math.max(0D, Math.min(100D, rate)) : 0D;
    }
}
