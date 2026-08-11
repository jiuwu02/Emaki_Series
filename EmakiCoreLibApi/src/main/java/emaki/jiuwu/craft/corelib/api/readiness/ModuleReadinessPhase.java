package emaki.jiuwu.craft.corelib.api.readiness;

/**
 * Which readiness transition a module just published.
 *
 * <p>Only the three states a module actually publishes are defined. No placeholder constant is
 * reserved for future use: a consumer's {@code switch} over this enum would break the moment a
 * fourth constant appeared, so a new phase must be introduced as a deliberate breaking change
 * rather than pre-declared here.</p>
 */
public enum ModuleReadinessPhase {

    /**
     * The module started replacing its data.
     *
     * <p>Invalidate anything cached from that module now. Reading it during this phase returns the
     * previous content or nothing at all, depending on how far the reload has progressed.</p>
     */
    LOADING,

    /**
     * The module's data is loaded and usable.
     *
     * <p>Rebuild caches here. This fires on the first load and again after every reload.</p>
     */
    READY,

    /**
     * The module was disabled.
     *
     * <p>It may be enabled again within the same server session, in which case {@link #LOADING} and
     * {@link #READY} follow. A listener is kept across this phase rather than dropped, because a
     * consumer has no way to notice that it would need to re-register.</p>
     */
    ABSENT
}
