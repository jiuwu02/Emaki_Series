package emaki.jiuwu.craft.strengthen.model;

/**
 * Durable semantic state of a strengthen attempt result.
 */
public enum AttemptOutcome {
    /** No externally visible charge or item result was committed. */
    NOT_COMMITTED,
    /** The attempt was charged and committed with a successful roll. */
    COMMITTED_SUCCESS,
    /** The attempt was charged and committed with a failed roll outcome. */
    COMMITTED_FAILURE,
    /** A partial charge could not be fully compensated and requires recovery. */
    COMPENSATION_PENDING
}
