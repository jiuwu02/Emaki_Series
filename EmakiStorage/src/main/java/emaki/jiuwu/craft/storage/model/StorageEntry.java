package emaki.jiuwu.craft.storage.model;

import emaki.jiuwu.craft.storage.api.model.StorageEntrySnapshot;

public final class StorageEntry {

    private final StorageKey key;
    private final String searchText;
    private final String sortName;

    private long amount;
    private long stackLimit;

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

    public long remove(long delta) {
        if (delta <= 0L) {
            return 0L;
        }
        long applied = Math.min(delta, amount);
        amount -= applied;
        return applied;
    }

    public boolean empty() {
        return amount <= 0L;
    }

    public StorageEntrySnapshot toSnapshot(int slotIndex, long effectiveLimit, long reservedAmount) {
        return new StorageEntrySnapshot(slotIndex, key.toItemStack(), amount, effectiveLimit, reservedAmount);
    }
}
