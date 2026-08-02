package emaki.jiuwu.craft.storage.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Per-op outcome of a {@link StorageBatchRequest}.
 *
 * <p>{@link #applied()} is positionally aligned with {@link StorageBatchRequest#ops()}, so index
 * {@code i} always describes op {@code i} even when that op applied nothing.
 *
 * @param applied         requested and actually applied unsigned amounts, one entry per op
 * @param failedIndex     index of the op that failed its pre-check, or {@code -1} when none did
 * @param failReasonKey   stable reason key for {@link #failedIndex}, or an empty string when none
 */
public record StorageBatchResult(@NotNull List<StorageAmount> applied,
        int failedIndex,
        @NotNull String failReasonKey) {

    /** Copies the amount list defensively and normalises the "no failure" markers. */
    public StorageBatchResult {
        applied = applied == null ? List.of() : List.copyOf(applied);
        failedIndex = failedIndex < 0 ? -1 : failedIndex;
        failReasonKey = failReasonKey == null ? "" : failReasonKey;
    }

    /**
     * Creates a result where every op applied fully.
     *
     * @param applied per-op amounts
     * @return the result
     */
    public static @NotNull StorageBatchResult of(@Nullable List<StorageAmount> applied) {
        return new StorageBatchResult(applied, -1, "");
    }

    /**
     * Creates a result for a batch aborted by one op's pre-check.
     *
     * @param applied per-op amounts, all zero for an all-or-nothing abort
     * @param failedIndex the offending op index
     * @param failReasonKey stable reason key
     * @return the result
     */
    public static @NotNull StorageBatchResult failedAt(@Nullable List<StorageAmount> applied,
            int failedIndex, @Nullable String failReasonKey) {
        return new StorageBatchResult(applied, failedIndex, failReasonKey);
    }

    /** {@return whether some op failed its pre-check} */
    public boolean failed() {
        return failedIndex >= 0;
    }

    /** {@return the total unsigned units actually applied across every op} */
    public long totalApplied() {
        long total = 0L;
        for (StorageAmount amount : applied) {
            total += amount.applied();
        }
        return total;
    }
}
