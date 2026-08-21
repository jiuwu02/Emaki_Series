package emaki.jiuwu.craft.strengthen.api.model;

/**
 * The structured buckets an enhancement target's persistent data is grouped into when the runtime
 * captures a variable snapshot.
 *
 * <p>A provider names the PDC path prefixes it owns per category through
 * {@code EnhancementTargetProvider#snapshotPartitions()}. Each category is published to conditions,
 * formulas and matchers under its own variable prefix, so the category a key falls into decides the
 * variable name a server owner writes in YAML.
 */
public enum TargetSnapshotCategory {

    /** Gameplay effects granted by the target; published as {@code target_effect_*}. */
    EFFECT("target_effect_"),

    /** Stacked enhancement layers or ledgers; published as {@code target_layer_*}. */
    LAYER("target_layer_"),

    /** Historical or provenance records; published as {@code target_audit_*}. */
    AUDIT("target_audit_"),

    /** Identity and bookkeeping fields; published as {@code target_meta_*}. */
    META("target_meta_");

    private final String variablePrefix;

    TargetSnapshotCategory(String variablePrefix) {
        this.variablePrefix = variablePrefix;
    }

    /** {@return the variable-name prefix this category publishes under} */
    public String variablePrefix() {
        return variablePrefix;
    }
}
