package emaki.jiuwu.craft.corelib.action;

/**
 * Failure classification carried by {@link ActionResult}.
 *
 * <p>Only the economy layer produces these values, and every constant here has a producer: the two
 * economy providers and {@code EconomyManager} emit them, {@code MoneyStage} maps them to
 * {@code CoreActionFailureKind}, and Strengthen / Item / Skills branch on
 * {@link #INSUFFICIENT_BALANCE} when deciding whether a charge was refused or genuinely failed.</p>
 *
 * <p>Action pipeline stages classify failures with {@code CoreActionFailureKind} instead and never reach
 * this enum. The v1 executor's unreachable constants have been removed.</p>
 */
public enum ActionErrorType {

    /** No failure; carried by a successful result. */
    NONE,

    /** A required argument was missing or unparseable. */
    INVALID_ARGUMENT,

    /** The provider threw, or reported a failure without a specific reason. */
    EXECUTION_EXCEPTION,

    /** No economy provider is installed, or the requested one is not available. */
    PROVIDER_UNAVAILABLE,

    /** The named currency does not exist in the provider. */
    CURRENCY_NOT_FOUND,

    /** The account exists but does not hold enough to cover the debit. */
    INSUFFICIENT_BALANCE
}
