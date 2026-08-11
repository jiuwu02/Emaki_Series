package emaki.jiuwu.craft.corelib.api.action;

/**
 * The three stage roles in a pipeline.
 *
 * <p>Their input/output contracts differ, so CoreLib keeps one registry table per kind and validates
 * stage position at compile time.</p>
 */
public enum CoreStageKind {

    /** Produces a target flow. Nothing in, targets out. */
    SOURCE,

    /** Filters or transforms a target flow. Targets in, targets out. */
    GATE,

    /** Consumes a target flow to produce an effect. Targets in, outcome out. */
    ACTION
}
