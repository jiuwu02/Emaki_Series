package emaki.jiuwu.craft.mobs.selector;

public record TargetLockConfig(boolean enabled, int intervalTicks) {

    public TargetLockConfig {
        intervalTicks = Math.max(20, intervalTicks);
    }

    public static TargetLockConfig disabled() {
        return new TargetLockConfig(false, 20);
    }
}
