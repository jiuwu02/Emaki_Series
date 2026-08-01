package emaki.jiuwu.craft.corelib.action;

/**
 * Failure classification carried by {@link ActionResult}.
 *
 * <p>Only the economy layer produces these values, and only {@code NONE}, {@code INVALID_ARGUMENT},
 * {@code EXECUTION_EXCEPTION}, {@code PROVIDER_UNAVAILABLE}, {@code CURRENCY_NOT_FOUND} and
 * {@code INSUFFICIENT_BALANCE} are emitted. Action pipeline stages classify failures with
 * {@code CoreActionFailureKind} instead and never reach this enum.</p>
 *
 * <p>The remaining constants are v1 executor leftovers with no producer and no reader left in this
 * repository; they are retained pending an explicit removal decision and must not be used in new code.</p>
 */
public enum ActionErrorType {
    NONE,
    INVALID_ARGUMENT,
    ACTION_NOT_FOUND,
    EXECUTION_EXCEPTION,
    INVALID_STATE,
    PROVIDER_UNAVAILABLE,
    CURRENCY_NOT_FOUND,
    INSUFFICIENT_BALANCE,
    WORLD_NOT_FOUND,
    TEMPLATE_NOT_FOUND,
    SYNTAX_ERROR,
    UNSUPPORTED
}
