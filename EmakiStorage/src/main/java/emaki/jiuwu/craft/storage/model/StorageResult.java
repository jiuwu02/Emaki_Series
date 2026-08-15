package emaki.jiuwu.craft.storage.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record StorageResult(@NotNull Status status,
        long requestedAmount,
        long appliedAmount,
        @Nullable String reasonKey) {

    public enum Status {

        SUCCESS,

        PARTIAL,

        FAILED,

        CANCELLED,

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

    public boolean applied() {
        return appliedAmount > 0L;
    }

    public boolean complete() {
        return status == Status.SUCCESS;
    }

    public long remainingAmount() {
        return Math.max(0L, requestedAmount - appliedAmount);
    }
}
