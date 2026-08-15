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

public final class StorageSortService {

    private final Collator prototype;

    public StorageSortService() {
        Collator collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
        collator.setStrength(Collator.SECONDARY);
        this.prototype = collator;
    }

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
