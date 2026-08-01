package emaki.jiuwu.craft.corelib.api.action.v2;

/**
 * Coarse failure classification for {@link CoreActionOutcome.Failure}.
 *
 * <p>Branch on this enum; read {@code reasonKey} for the stable module-specific detail. Do not treat
 * {@code reasonKey} as an exhaustive enum.</p>
 */
public enum CoreActionFailureKind {

    /** The pipeline or one of its stages is misconfigured. Reported at compile time when possible. */
    INVALID_CONFIG,

    /** A required context key or subject was missing at runtime. */
    MISSING_CONTEXT,

    /** The stage ran on a thread it may not touch, or the platform cannot supply its domain. */
    WRONG_THREAD,

    /** The stage exceeded its declared timeout. */
    TIMEOUT,

    /** The owner plugin of a stage is no longer enabled. */
    OWNER_DISABLED,

    /** The stage threw an unexpected exception. */
    INTERNAL_ERROR,

    /** A domain rule refused the effect, for example an economy balance check. */
    REJECTED
}
