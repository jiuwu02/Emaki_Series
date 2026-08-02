package emaki.jiuwu.craft.storage.model;

import emaki.jiuwu.craft.storage.api.model.StorageEntrySnapshot;

/**
 * One stored item kind and its amount.
 *
 * <p>{@code searchText} and {@code sortName} are pre-computed once when the entry is created so
 * search and sort never re-strip MiniMessage formatting per keystroke. Both caches live on the
 * entry and are released with it — they are deliberately not held in any static map, and no
 * {@code ThreadLocal} is used because Folia region threads would accumulate copies.
 */
public final class StorageEntry {

    private final StorageKey key;
    private final String searchText;
    private final String sortName;

    private long amount;
    private long stackLimit;

    /**
     * @param key        the normalised item key
     * @param amount     initial amount, clamped to at least zero
     * @param stackLimit per-entry ceiling; {@code 0} inherits the player default
     * @param searchText pre-computed plain text of name and lore, already lower-cased
     * @param sortName   pre-computed plain display name used as the sort key
     */
    public StorageEntry(StorageKey key, long amount, long stackLimit, String searchText, String sortName) {
        this.key = key;
        this.amount = Math.max(0L, amount);
        this.stackLimit = Math.max(0L, stackLimit);
        this.searchText = searchText == null ? "" : searchText;
        this.sortName = sortName == null ? "" : sortName;
    }

    public StorageKey key() {
        return key;
    }

    public long amount() {
        return amount;
    }

    public long stackLimit() {
        return stackLimit;
    }

    public String searchText() {
        return searchText;
    }

    public String sortName() {
        return sortName;
    }

    public void stackLimit(long stackLimit) {
        this.stackLimit = Math.max(0L, stackLimit);
    }

    /**
     * Adds to the stored amount with explicit overflow protection.
     *
     * @param delta how much to add, must be non-negative
     * @return the amount actually added, which is less than {@code delta} when the ceiling is hit
     */
    public long add(long delta, long effectiveLimit) {
        if (delta <= 0L) {
            return 0L;
        }
        long ceiling = effectiveLimit <= 0L ? Long.MAX_VALUE : effectiveLimit;
        long room = ceiling - amount;
        if (room <= 0L) {
            return 0L;
        }
        long applied = Math.min(delta, room);
        amount += applied;
        return applied;
    }

    /**
     * Debits the stored amount.
     *
     * @param delta how much to remove, must be non-negative
     * @return the amount actually removed, capped by what was stored
     */
    public long remove(long delta) {
        if (delta <= 0L) {
            return 0L;
        }
        long applied = Math.min(delta, amount);
        amount -= applied;
        return applied;
    }

    /** {@return whether this entry holds nothing and should be dropped from the order list} */
    public boolean empty() {
        return amount <= 0L;
    }

    /**
     * Builds the immutable API view of this entry.
     *
     * @param slotIndex      the logical slot index this entry currently occupies
     * @param effectiveLimit the resolved three-level stack limit
     * @param reservedAmount how many of {@link #amount()} are held by outstanding reservations
     * @return a detached snapshot
     */
    public StorageEntrySnapshot toSnapshot(int slotIndex, long effectiveLimit, long reservedAmount) {
        return new StorageEntrySnapshot(slotIndex, key.toItemStack(), amount, effectiveLimit, reservedAmount);
    }
}
