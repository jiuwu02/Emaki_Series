package emaki.jiuwu.craft.storage.service;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.SortMode;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;

/**
 * Computes a new entry order for the explicit tidy-up action.
 *
 * <p>Chinese names are ordered with the JDK's own {@link Collator} rather than a bundled pinyin
 * library. {@code Collator} is <strong>not thread-safe</strong>, so a fresh clone is taken inside
 * each sort call; sharing one instance across Folia region threads would corrupt its internal
 * state.
 *
 * <p>Comparison is pure computation and may run off the owner thread. Applying the result must
 * happen on the owner thread and must re-check the storage revision: if a deposit or withdrawal
 * landed while the comparison was running, the computed order is stale and is discarded rather
 * than applied.
 */
public final class StorageSortService {

    private final Collator prototype;

    public StorageSortService() {
        Collator collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        collator.setStrength(Collator.SECONDARY);
        this.prototype = collator;
    }

    /**
     * Computes the sorted key order without mutating the storage.
     *
     * @param storage the storage to order
     * @param mode    the requested ordering
     * @return the new key order
     */
    public List<StorageKey> computeOrder(PlayerStorage storage, SortMode mode) {
        if (storage == null || storage.entryCount() <= 1) {
            return storage == null ? List.of() : storage.entryOrder();
        }
        SortMode active = mode == null ? SortMode.AMOUNT_DESC : mode;
        Collator collator = (Collator) prototype.clone();
        List<StorageKey> order = new ArrayList<>(storage.entryOrder());
        Comparator<StorageKey> comparator = comparatorFor(storage, active, collator);
        order.sort(comparator);
        return order;
    }

    /**
     * Applies a previously computed order when the storage has not changed since.
     *
     * @param storage          the storage to reorder
     * @param order            the computed order
     * @param expectedRevision the revision observed when the order was computed
     * @return whether the order was applied
     */
    public boolean applyOrder(PlayerStorage storage, List<StorageKey> order, long expectedRevision) {
        if (storage == null || order == null || order.isEmpty()) {
            return false;
        }
        if (storage.revision() != expectedRevision) {
            return false;
        }
        storage.reorder(order);
        storage.markDirty();
        return true;
    }

    /**
     * Sorts in place on the current thread, used when already on the owner thread with a small
     * entry count.
     *
     * @param storage the storage to reorder
     * @param mode    the requested ordering
     * @return whether anything was applied
     */
    public boolean sortNow(PlayerStorage storage, SortMode mode) {
        if (storage == null || storage.entryCount() <= 1) {
            return false;
        }
        long revision = storage.revision();
        return applyOrder(storage, computeOrder(storage, mode), revision);
    }

    private Comparator<StorageKey> comparatorFor(PlayerStorage storage, SortMode mode, Collator collator) {
        Comparator<StorageKey> primary = switch (mode.dimension()) {
            case MATERIAL -> Comparator.comparing(key -> key.material().name());
            case NAME -> (left, right) -> collator.compare(sortName(storage, left), sortName(storage, right));
            case AMOUNT -> Comparator.comparingLong(key -> amountOf(storage, key));
        };
        Comparator<StorageKey> directed = mode.ascending() ? primary : primary.reversed();
        // Stable secondary key: identical names must not swap positions between sorts.
        return directed.thenComparing(key -> key.material().name());
    }

    private String sortName(PlayerStorage storage, StorageKey key) {
        StorageEntry entry = storage.entry(key);
        return entry == null ? "" : entry.sortName();
    }

    private long amountOf(PlayerStorage storage, StorageKey key) {
        StorageEntry entry = storage.entry(key);
        return entry == null ? 0L : entry.amount();
    }
}
