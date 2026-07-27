package emaki.jiuwu.craft.storage.api.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Outcome of a storage mutation.
 *
 * <p>Partial success is a first-class result: depositing 1000 units into a slot with 300 room
 * left reports {@code status == PARTIAL}, {@code appliedAmount == 300} and
 * {@code remainingAmount == 700}. Callers must inspect {@link #appliedAmount()} rather than
 * assuming the requested amount was applied in full.
 *
 * @param status          the outcome classification
 * @param requestedAmount the amount the caller asked for
 * @param appliedAmount   the amount actually applied
 * @param reasonKey       a stable machine-readable reason, {@code null} on full success
 */
public record StorageResult(@NotNull Status status,
        long requestedAmount,
        long appliedAmount,
        @Nullable String reasonKey) {

    /** Outcome classification for a storage mutation. */
    public enum Status {
        /** The full requested amount was applied. */
        SUCCESS,
        /** Part of the requested amount was applied; the rest was rejected. */
        PARTIAL,
        /** Nothing was applied. */
        FAILED,
        /** A listener cancelled the operation. */
        CANCELLED,
        /** The plugin, player data or owning thread was unavailable. */
        UNAVAILABLE
    }

    public static @NotNull StorageResult success(long amount) {
        return new StorageResult(Status.SUCCESS, amount, amount, null);
    }

    public static @NotNull StorageResult partial(long requested, long applied, @NotNull String reasonKey) {
        return new StorageResult(Status.PARTIAL, requested, applied, reasonKey);
    }

    public static @NotNull StorageResult failed(long requested, @NotNull String reasonKey) {
        return new StorageResult(Status.FAILED, requested, 0L, reasonKey);
    }

    public static @NotNull StorageResult cancelled(long requested) {
        return new StorageResult(Status.CANCELLED, requested, 0L, "cancelled");
    }

    public static @NotNull StorageResult unavailable() {
        return new StorageResult(Status.UNAVAILABLE, 0L, 0L, "unavailable");
    }

    /** {@return whether any amount at all was applied} */
    public boolean applied() {
        return appliedAmount > 0L;
    }

    /** {@return whether the full requested amount was applied} */
    public boolean complete() {
        return status == Status.SUCCESS;
    }

    /** {@return the amount that could not be applied, never negative} */
    public long remainingAmount() {
        return Math.max(0L, requestedAmount - appliedAmount);
    }
}
