package emaki.jiuwu.craft.station.config;

/**
 * Save-timing settings from {@code config.yml}.
 *
 * <p>The autosave interval is deliberately shorter than the warehouse's own, because a queue file
 * carries materials that were already debited: losing recent queue changes loses player property,
 * whereas losing recent warehouse changes only loses the deposit.
 *
 * @param autosaveIntervalSeconds how often dirty queues are flushed
 * @param saveOnSubmit            whether a successful submission writes its file immediately instead of
 *                                waiting for the next autosave
 */
public record PersistenceSettings(int autosaveIntervalSeconds, boolean saveOnSubmit) {

    /** {@return the shipped defaults} */
    public static PersistenceSettings defaults() {
        return new PersistenceSettings(60, true);
    }

    /**
     * Clamps every field into its documented range.
     *
     * @return a settings record whose values are all in range
     */
    public PersistenceSettings normalized() {
        return new PersistenceSettings(Math.clamp(autosaveIntervalSeconds, 5, 3_600), saveOnSubmit);
    }
}
