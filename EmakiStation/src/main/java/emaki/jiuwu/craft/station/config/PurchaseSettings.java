package emaki.jiuwu.craft.station.config;

/**
 * Paid queue-slot settings from {@code config.yml}.
 *
 * <p>A station additionally has to opt in through its own {@code queue.allow_purchase}, so this is the
 * server-wide master switch rather than the only gate.
 *
 * @param enabled  the master switch; when false no station offers queue purchases
 * @param costFile the price file name resolved inside the plugin data folder
 */
public record PurchaseSettings(boolean enabled, String costFile) {

    /** The price file name shipped with the plugin. */
    public static final String DEFAULT_COST_FILE = "queue_costs.yml";

    /** {@return the shipped defaults} */
    public static PurchaseSettings defaults() {
        return new PurchaseSettings(true, DEFAULT_COST_FILE);
    }

    /**
     * Clamps every field into its documented range.
     *
     * @return a settings record whose values are all usable
     */
    public PurchaseSettings normalized() {
        String safeFile = costFile == null || costFile.isBlank() ? DEFAULT_COST_FILE : costFile.trim();
        return new PurchaseSettings(enabled, safeFile);
    }
}
