package emaki.jiuwu.craft.kits.api.model;

/**
 * The eight kit categories exposed by EmakiKits.
 *
 * <p>The enum mirrors the {@code type} values accepted in {@code kits/*.yml}. Each category
 * differs only in trigger, eligibility and persistence shape; all of them share the same
 * reward, audit and idempotency core.
 */
public enum KitTypeView {

    /** Granted on a player's first join after an invite code is supplied. */
    JOIN,

    /** Granted when a newcomer binds to an existing player. */
    INVITE,

    /** Claimable once per day, keyed by the configured time zone. */
    DAILY,

    /** Claimable once per week, keyed by the configured time zone. */
    WEEKLY,

    /** Claimable once per month, keyed by the configured time zone. */
    MONTHLY,

    /** Redeemed with a global-once or per-player-once CDK code. */
    CDK,

    /** Claimable after selecting configured options through the CoreLib GUI. */
    CHOICE,

    /** Fixed, weighted-random, purchasable, open-count and time-limited kit. */
    GENERIC
}
