package emaki.jiuwu.craft.station.config;

public record PurchaseSettings(boolean enabled, String costFile) {

    public static final String DEFAULT_COST_FILE = "queue_costs.yml";

    public static PurchaseSettings defaults() {
        return new PurchaseSettings(true, DEFAULT_COST_FILE);
    }

    public PurchaseSettings normalized() {
        String safeFile = costFile == null || costFile.isBlank() ? DEFAULT_COST_FILE : costFile.trim();
        return new PurchaseSettings(enabled, safeFile);
    }
}
