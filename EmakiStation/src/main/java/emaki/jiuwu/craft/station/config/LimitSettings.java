package emaki.jiuwu.craft.station.config;

public record LimitSettings(int maxPendingClaim, int warnMaterialTypes) {

    public static LimitSettings defaults() {
        return new LimitSettings(100, 50);
    }

    public LimitSettings normalized() {
        return new LimitSettings(Math.clamp(maxPendingClaim, 1, 10_000),
                Math.clamp(warnMaterialTypes, 1, 10_000));
    }
}
