package emaki.jiuwu.craft.corelib.api.itemsource;

/**
 * Why an item source reference is or is not resolvable right now.
 *
 * <p>Each value maps one-to-one onto the old {@code ItemSourceProbeStatus}, except that
 * {@code RESOLVER_MISSING} is renamed {@link #PROVIDER_MISSING} to match the provider vocabulary.
 *
 * <p>{@link #PROVIDER_MISSING} and {@link #SOURCE_NOT_FOUND} must stay distinct in user-facing
 * messages. The first means no installed plugin claims that prefix at all; the second means the right
 * plugin is there but has no such item. Collapsing them into one "item not found" is what makes a
 * server owner hunt for a typo in a recipe that is actually written correctly.
 */
public enum ItemSourceProbeState {

    /** The reference resolves to a real item right now. */
    READY,

    /** The reference itself is malformed, or the probed provider does not handle this kind. */
    INVALID_SOURCE,

    /** No registered provider claims this kind or prefix. */
    PROVIDER_MISSING,

    /** The provider is installed but has not finished loading its items. */
    PROVIDER_NOT_READY,

    /** The provider is ready but holds no item under this identifier. */
    SOURCE_NOT_FOUND,

    /** The provider's API did not link, usually a version mismatch. */
    INCOMPATIBLE,

    /** The provider threw while resolving. */
    RESOLUTION_ERROR
}
