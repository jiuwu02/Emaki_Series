package emaki.jiuwu.craft.station.config;

public record LimitSettings(int maxPendingClaim, int warnMaterialTypes, long batchMultiplierMax) {

    public static LimitSettings defaults() {
        return new LimitSettings(100, 50, 0L);
    }

    public LimitSettings normalized() {
        return new LimitSettings(Math.clamp(maxPendingClaim, 1, 10_000),
                Math.clamp(warnMaterialTypes, 1, 10_000),
                Math.max(0L, batchMultiplierMax));
    }

    public boolean hasBatchCeiling() {
        return batchMultiplierMax > 0L;
    }
}
