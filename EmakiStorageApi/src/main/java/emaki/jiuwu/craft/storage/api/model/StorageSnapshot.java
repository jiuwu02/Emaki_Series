package emaki.jiuwu.craft.storage.api.model;

import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;

/**
 * Immutable snapshot of one player's storage.
 *
 * <p>Entries are ordered by logical slot index. The snapshot is detached from live state: it
 * never observes later deposits or withdrawals.
 *
 * @param playerId          the owning player
 * @param entries           entries in slot order
 * @param capacity          the capacity breakdown at snapshot time
 * @param defaultStackLimit the player-level per-slot ceiling, {@code 0} meaning inherit config
 * @param sortMode          the persisted sort mode id
 */
public record StorageSnapshot(@NotNull UUID playerId,
        @NotNull List<StorageEntrySnapshot> entries,
        @NotNull StorageCapacity capacity,
        long defaultStackLimit,
        @NotNull String sortMode) {

    public StorageSnapshot(@NotNull UUID playerId,
            @NotNull List<StorageEntrySnapshot> entries,
            @NotNull StorageCapacity capacity,
            long defaultStackLimit,
            @NotNull String sortMode) {
        this.playerId = playerId;
        this.entries = List.copyOf(entries);
        this.capacity = capacity;
        this.defaultStackLimit = defaultStackLimit;
        this.sortMode = sortMode;
    }

    /** {@return an empty snapshot used when the plugin is unavailable or data is missing} */
    public static @NotNull StorageSnapshot empty(@NotNull UUID playerId) {
        return new StorageSnapshot(playerId, List.of(), StorageCapacity.empty(), 0L, "amount_desc");
    }

    /** {@return how many distinct entries are stored} */
    public int entryCount() {
        return entries.size();
    }

    /** {@return the summed amount across every entry, saturating at {@link Long#MAX_VALUE}} */
    public long totalAmount() {
        long total = 0L;
        for (StorageEntrySnapshot entry : entries) {
            long next = total + entry.amount();
            if (next < total) {
                return Long.MAX_VALUE;
            }
            total = next;
        }
        return total;
    }
}
