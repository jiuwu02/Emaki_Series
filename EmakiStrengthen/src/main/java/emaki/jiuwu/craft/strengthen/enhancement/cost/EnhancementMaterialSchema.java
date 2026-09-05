package emaki.jiuwu.craft.strengthen.enhancement.cost;

public final class EnhancementMaterialSchema {

    public static final int LEGACY_VERSION = 1;
    public static final int CANONICAL_VERSION = 2;

    private EnhancementMaterialSchema() {
    }

    public static boolean supported(int schemaVersion) {
        return schemaVersion >= LEGACY_VERSION && schemaVersion <= CANONICAL_VERSION;
    }
}
