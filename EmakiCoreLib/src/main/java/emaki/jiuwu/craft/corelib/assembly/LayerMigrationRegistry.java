package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for layer migrations. Each module registers its migrations here.
 * <p>
 * When a layer snapshot is loaded with a version lower than the current version,
 * the registry provides the ordered chain of migrations to apply.
 */
public final class LayerMigrationRegistry {

    private final Map<String, List<LayerMigration>> migrations = new LinkedHashMap<>();

    public LayerMigrationRegistry() {
    }

    /**
     * Register a migration.
     */
    public void register(LayerMigration migration) {
        if (migration == null) {
            return;
        }
        migrations.computeIfAbsent(migration.namespace(), _ -> new ArrayList<>()).add(migration);
    }

    /**
     * Get the ordered migration chain for a namespace from a given version to the target version.
     *
     * @param namespace     the layer namespace
     * @param fromVersion   the current (old) version of the snapshot
     * @param targetVersion the desired target version
     * @return ordered list of migrations to apply, empty if none needed
     */
    public List<LayerMigration> migrationChain(String namespace, int fromVersion, int targetVersion) {
        List<LayerMigration> all = migrations.get(namespace);
        if (all == null || all.isEmpty() || fromVersion >= targetVersion) {
            return List.of();
        }
        List<LayerMigration> chain = new ArrayList<>();
        int currentVersion = fromVersion;
        List<LayerMigration> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparingInt(LayerMigration::fromVersion));

        while (currentVersion < targetVersion) {
            boolean found = false;
            for (LayerMigration migration : sorted) {
                if (migration.fromVersion() == currentVersion && migration.toVersion() > currentVersion) {
                    chain.add(migration);
                    currentVersion = migration.toVersion();
                    found = true;
                    break;
                }
            }
            if (!found) {
                break;
            }
        }
        return List.copyOf(chain);
    }

    /**
     * Apply the migration chain to an audit map.
     *
     * @param namespace     the layer namespace
     * @param audit         the mutable audit map
     * @param fromVersion   the snapshot's current version
     * @param targetVersion the desired version
     * @return the migrated audit map
     */
    public Map<String, Object> applyMigrations(String namespace, Map<String, Object> audit, int fromVersion, int targetVersion) {
        List<LayerMigration> chain = migrationChain(namespace, fromVersion, targetVersion);
        Map<String, Object> current = audit;
        for (LayerMigration migration : chain) {
            current = migration.migrateAudit(current);
        }
        return current;
    }
}
