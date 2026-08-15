package emaki.jiuwu.craft.station.config;

public record StorageSettings(boolean enabled, int batchMaxOps) {

    public static StorageSettings defaults() {
        return new StorageSettings(true, 200);
    }

    public StorageSettings normalized() {
        return new StorageSettings(enabled, Math.clamp(batchMaxOps, 1, 10_000));
    }
}
