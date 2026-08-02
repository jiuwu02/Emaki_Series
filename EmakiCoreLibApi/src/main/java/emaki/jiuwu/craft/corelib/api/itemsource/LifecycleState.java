package emaki.jiuwu.craft.corelib.api.itemsource;

/**
 * How far along a provider's backing plugin is.
 *
 * <p>Values map one-to-one onto the old package-private {@code ManagedItemSourceResolver.State}.
 */
public enum LifecycleState {

    /** The backing plugin is not installed or not enabled. */
    ABSENT,

    /** The backing plugin is enabled but has not finished loading its items. */
    WAITING,

    /** The provider can resolve items right now. */
    READY,

    /** The backing plugin is present but its API did not link. */
    INCOMPATIBLE
}
