package emaki.jiuwu.craft.corelib.assembly;

import java.util.Map;

public interface LayerMigration {

    String namespace();

    int fromVersion();

    int toVersion();

    Map<String, Object> migrateAudit(Map<String, Object> audit);
}
