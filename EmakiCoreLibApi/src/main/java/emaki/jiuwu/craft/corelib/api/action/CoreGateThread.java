package emaki.jiuwu.craft.corelib.api.action;

/**
 * What thread access a gate stage needs.
 *
 * <p>This is what lets one pipeline span several domains: a {@link #PURE} gate can be folded into
 * either neighbouring domain without its own dispatch, while {@link #NEEDS_ENTITY_READ} forces the
 * entity domain. In v1 a whole action line was locked to a single domain.</p>
 */
public enum CoreGateThread {

    /** Touches no Bukkit state. Runs anywhere, folds into any adjacent domain. */
    PURE,

    /** Reads entity state such as health or type. Requires the entity's owning thread. */
    NEEDS_ENTITY_READ,

    /** Reads world or block state. Requires the region's owning thread. */
    NEEDS_REGION_READ
}
