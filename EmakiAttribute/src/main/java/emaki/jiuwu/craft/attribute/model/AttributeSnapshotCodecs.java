package emaki.jiuwu.craft.attribute.model;

import emaki.jiuwu.craft.corelib.pdc.SnapshotCodec;

public final class AttributeSnapshotCodecs {

    private AttributeSnapshotCodecs() {
    }

    public static final SnapshotCodec<AttributeSnapshot> ATTRIBUTE_SNAPSHOT = SnapshotCodec.versionedYaml(
            AttributeSnapshot.CURRENT_SCHEMA_VERSION,
            AttributeSnapshot::toMap,
            AttributeSnapshot::fromMap
    );

    public static final SnapshotCodec<ProjectileDamageSnapshot> PROJECTILE_DAMAGE_SNAPSHOT = SnapshotCodec.versionedYaml(
            ProjectileDamageSnapshot.CURRENT_SCHEMA_VERSION,
            ProjectileDamageSnapshot::toMap,
            ProjectileDamageSnapshot::fromMap
    );
}
