package emaki.jiuwu.craft.corelib.api.math;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Percentage-roll helpers shared by gameplay chance checks.
 *
 * <p>Rates are plain percentages in {@code [0, 100]}. The engine keeps no shared mutable state
 * and draws from {@link ThreadLocalRandom}, so it is safe to call from any thread.
 */
public final class CraftRollEngine {

    private CraftRollEngine() {}

    /**
     * Rolls a percentage chance.
     *
     * <p>The result is {@code true} with probability {@code rate / 100}: a {@code rate} of zero
     * or below (and {@code NaN}) always misses, while {@code rate >= 100} always hits.
     *
     * @param rate percentage in {@code [0, 100]}; values outside the range are not clamped here
     * @return whether the roll succeeded
     */
    public static boolean roll(double rate) {
        return ThreadLocalRandom.current().nextDouble(100D) < rate;
    }

    /**
     * Clamps a percentage into {@code [0, 100]}.
     *
     * <p>Non-finite input ({@code NaN} and the infinities) becomes {@code 0}.
     *
     * @param rate raw rate
     * @return the finite rate within {@code [0, 100]}
     */
    public static double clamp(double rate) {
        return Double.isFinite(rate) ? Math.max(0D, Math.min(100D, rate)) : 0D;
    }
}
