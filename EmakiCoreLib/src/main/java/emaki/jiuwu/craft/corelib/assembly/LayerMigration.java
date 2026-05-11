package emaki.jiuwu.craft.corelib.assembly;

import java.util.Map;

/**
 * Interface for migrating an item layer snapshot from one schema version to the next.
 * <p>
 * Each module registers its own migrations (e.g. StrengthenLayerMigration_v1_to_v2).
 * When an item is rebuilt and its layer version is below the current version,
 * the migration chain is executed sequentially.
 */
public interface LayerMigration {

    /**
     * @return the namespace this migration applies to (e.g. "strengthen", "gem", "forge")
     */
    String namespace();

    /**
     * @return the source version this migration upgrades from
     */
    int fromVersion();

    /**
     * @return the target version this migration upgrades to
     */
    int toVersion();

    /**
     * Migrate the audit data from the old format to the new format.
     *
     * @param audit the mutable audit map from the layer snapshot
     * @return the migrated audit map (may be the same instance, mutated)
     */
    Map<String, Object> migrateAudit(Map<String, Object> audit);
}
