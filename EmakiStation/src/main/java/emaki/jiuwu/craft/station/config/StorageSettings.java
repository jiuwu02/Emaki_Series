package emaki.jiuwu.craft.station.config;

/**
 * Warehouse-channel settings from {@code config.yml}.
 *
 * @param enabled     the master switch; when false EmakiStation never calls EmakiStorage at all
 * @param batchMaxOps the largest batch EmakiStation will assemble, mirroring the storage-side cap
 */
public record StorageSettings(boolean enabled, int batchMaxOps) {

    /** {@return the shipped defaults} */
    public static StorageSettings defaults() {
        return new StorageSettings(true, 200);
    }

    /**
     * Clamps every field into its documented range.
     *
     * @return a settings record whose values are all in range
     */
    public StorageSettings normalized() {
        return new StorageSettings(enabled, Math.clamp(batchMaxOps, 1, 10_000));
    }
}
