package emaki.jiuwu.craft.storage.api.model;

/**
 * Amount payload for a storage transfer.
 *
 * @param requested how many units the caller requested
 * @param applied   how many units the operation actually transferred
 */
public record StorageAmount(long requested, long applied) {

    /** {@return the unapplied remainder, never negative} */
    public long remaining() {
        return Math.max(0L, requested - applied);
    }

    /** {@return whether the full requested amount was applied} */
    public boolean complete() {
        return applied >= requested;
    }
}
