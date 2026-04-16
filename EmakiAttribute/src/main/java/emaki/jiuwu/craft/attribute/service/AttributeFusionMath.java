package emaki.jiuwu.craft.attribute.service;

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;

final class AttributeFusionMath {

    static final int LEGACY_SNAPSHOT_SCHEMA_VERSION = 1;
    static final int FUSED_COMBAT_SNAPSHOT_SCHEMA_VERSION = 2;

    private AttributeFusionMath() {
    }

    static boolean usesFusedCombatValues(AttributeSnapshot snapshot) {
        return snapshot != null && snapshot.schemaVersion() >= FUSED_COMBAT_SNAPSHOT_SCHEMA_VERSION;
    }

    static double percentFactor(double percentBonus, boolean clampNonNegative) {
        double factor = 1D + (percentBonus / 100D);
        return clampNonNegative ? Math.max(0D, factor) : factor;
    }

    static double toEffectiveFlat(double rawFlat, double percentBonus, boolean clampNonNegative) {
        return rawFlat * percentFactor(percentBonus, clampNonNegative);
    }
}
