package emaki.jiuwu.craft.corelib.api.action.execution;

/** Overall outcome of an externally requested action execution. */
public enum CoreActionExecutionStatus {

    /** Every executable stage completed successfully. */
    SUCCESS,

    /** The request was valid but had no work to perform. */
    SKIPPED,

    /** Some requested work completed and some failed or was skipped. */
    PARTIAL,

    /** The supplied action configuration could not be compiled. */
    COMPILE_FAILED,

    /** Compilation succeeded, but execution failed. */
    EXECUTION_FAILED,

    /** The request itself was incomplete or invalid. */
    INVALID_REQUEST,

    /** The action execution service is not currently available. */
    UNAVAILABLE
}
