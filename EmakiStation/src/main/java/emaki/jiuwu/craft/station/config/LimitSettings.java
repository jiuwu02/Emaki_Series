package emaki.jiuwu.craft.station.config;

/**
 * Volume ceilings from {@code config.yml}.
 *
 * @param maxPendingClaim   how many undelivered entries one player may accumulate before new
 *                          submissions are refused
 * @param warnMaterialTypes the requirement count above which a recipe only logs a warning
 * @param batchMultiplierMax the largest accepted batch, or {@code 0} for no explicit ceiling
 */
public record LimitSettings(int maxPendingClaim, int warnMaterialTypes, long batchMultiplierMax) {

    /** {@return the shipped defaults} */
    public static LimitSettings defaults() {
        return new LimitSettings(100, 50, 0L);
    }

    /**
     * Clamps every field into its documented range.
     *
     * @return a settings record whose values are all in range
     */
    public LimitSettings normalized() {
        return new LimitSettings(Math.clamp(maxPendingClaim, 1, 10_000),
                Math.clamp(warnMaterialTypes, 1, 10_000),
                Math.max(0L, batchMultiplierMax));
    }

    /** {@return whether an explicit batch ceiling is configured} */
    public boolean hasBatchCeiling() {
        return batchMultiplierMax > 0L;
    }
}
