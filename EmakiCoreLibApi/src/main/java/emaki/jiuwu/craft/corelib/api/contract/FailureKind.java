package emaki.jiuwu.craft.corelib.api.contract;

/**
 * Machine-readable classification of why an Emaki API call did not fully succeed.
 *
 * <p>Every {@link EmakiResult.Failure} carries exactly one kind. Callers are expected to branch on
 * this enum rather than on {@link EmakiResult#reasonKey()}, because {@code reasonKey} identifies the
 * concrete situation (and may grow over time) while the kind set stays deliberately small and
 * stable.
 *
 * <p>The distinction between {@link #UNAVAILABLE} and every other constant is the core reason this
 * contract exists: an absent plugin must never be indistinguishable from a legitimate business
 * outcome.
 */
public enum FailureKind {

    /**
     * The backing plugin is not installed, not enabled, has not installed its API bridge, or is
     * temporarily unusable (for example during a reload). Retrying later may succeed.
     */
    UNAVAILABLE,

    /**
     * The requested identifier, definition, recipe, or stored player data does not exist. The call
     * itself was well formed.
     */
    NOT_FOUND,

    /**
     * An argument was rejected before any business logic ran: {@code null}, blank, negative, or out
     * of range. This always indicates a caller bug.
     */
    INVALID_INPUT,

    /**
     * Business rules refused the operation. Conditions were unmet, resources were insufficient,
     * slots were full, or a level requirement was not reached.
     */
    REJECTED,

    /** An event listener cancelled the operation. */
    CANCELLED,

    /** The target player is offline, so no player-scoped work could be performed. */
    TARGET_OFFLINE,

    /**
     * The calling thread does not own the target object. On Folia this means the current thread is
     * not the region owner for the entity or location involved; on Paper it usually means the call
     * happened off the main thread.
     */
    WRONG_THREAD,

    /** The implementation threw an unexpected exception. Treat as a bug in the backing plugin. */
    INTERNAL_ERROR
}
