package emaki.jiuwu.craft.corelib.api.action.execution;

/** Outcome of one stage within an action execution. */
public enum CoreActionStageExecutionStatus {

    /** The stage completed successfully. */
    SUCCESS,

    /** The stage had no work to perform. */
    SKIPPED,

    /** The stage completed for only part of its target flow. */
    PARTIAL,

    /** The stage did not complete successfully. */
    FAILURE
}
