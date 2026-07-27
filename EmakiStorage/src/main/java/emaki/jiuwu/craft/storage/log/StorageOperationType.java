package emaki.jiuwu.craft.storage.log;

/**
 * Operation classes recorded in the flow log.
 *
 * <p>The three {@code ADMIN_*} kinds are always recorded regardless of {@code logging.enabled}
 * and {@code logging.sources}: an admin disables logging to save disk, which is no reason to lose
 * the records most likely to be needed for a dispute.
 */
public enum StorageOperationType {

    DEPOSIT,
    WITHDRAW,
    UNLOCK,
    ADMIN_SET,
    ADMIN_GIVE,
    ADMIN_CLEAR,
    /** Items returned or locked because capacity shrank below occupancy. */
    OVERFLOW;

    /** {@return whether this kind bypasses the logging toggles} */
    public boolean forced() {
        return this == ADMIN_SET || this == ADMIN_GIVE || this == ADMIN_CLEAR;
    }
}
