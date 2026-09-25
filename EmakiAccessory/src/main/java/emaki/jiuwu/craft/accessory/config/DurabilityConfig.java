package emaki.jiuwu.craft.accessory.config;

public record DurabilityConfig(boolean enabled, int damagePerHit) {

    public static DurabilityConfig defaults() {
        return new DurabilityConfig(false, 1);
    }

    public DurabilityConfig {
        damagePerHit = Math.max(1, damagePerHit);
    }
}