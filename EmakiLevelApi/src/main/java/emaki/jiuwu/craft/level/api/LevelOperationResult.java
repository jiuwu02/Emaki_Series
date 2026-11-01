package emaki.jiuwu.craft.level.api;

import java.util.Map;

/**
 * Outcome record of one recorded level operation.
 *
 * <p>Every state-changing level call resolves to one of these records. {@link #success()} is the
 * primary outcome flag; {@code reason} carries a stable machine-readable key ({@code "success"}
 * for applied operations, a diagnostic token for refusals) meant for message placeholders and
 * logging rather than exhaustive enum switching. The before/after snapshots ({@code oldLevel},
 * {@code newLevel}, {@code oldExp}, {@code newExp}) are only meaningful when {@code success} is
 * {@code true}; results built by {@link #failure} zero them.
 *
 * <p>The compact constructor normalizes a {@code null} {@code reason} or {@code typeId} to an
 * empty string and a {@code null} {@code data} map to an empty immutable map. Instances are
 * immutable and safe to share across threads.
 *
 * @param success       whether the operation was applied
 * @param reason        stable machine-readable outcome key; {@code null} becomes an empty string
 * @param operationType the operation this result describes
 * @param typeId        the targeted level type; {@code null} becomes an empty string
 * @param oldLevel      level before the operation; {@code 0} in failure results
 * @param newLevel      level after the operation; {@code 0} in failure results
 * @param oldExp        progress before the operation; {@code 0} in failure results
 * @param newExp        progress after the operation; {@code 0} in failure results
 * @param amount        magnitude the operation applied, experience or levels depending on the
 *                      operation type; {@code 0} in failure results
 * @param data          operation-specific diagnostic payload; copied into an immutable map
 */
public record LevelOperationResult(boolean success,
        String reason,
        LevelOperationType operationType,
        String typeId,
        int oldLevel,
        int newLevel,
        double oldExp,
        double newExp,
        double amount,
        Map<String, Object> data) {

    /**
     * Normalizes nullable identity fields and freezes the payload map.
     *
     * @param success       whether the operation was applied
     * @param reason        outcome key; {@code null} becomes an empty string
     * @param operationType the operation this result describes
     * @param typeId        the targeted level type; {@code null} becomes an empty string
     * @param oldLevel      level before the operation
     * @param newLevel      level after the operation
     * @param oldExp        progress before the operation
     * @param newExp        progress after the operation
     * @param amount        magnitude applied by the operation
     * @param data          diagnostic payload; {@code null} becomes an empty immutable map
     */
    public LevelOperationResult {
        reason = reason == null ? "" : reason;
        typeId = typeId == null ? "" : typeId;
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    /**
     * Creates a successful result carrying the before/after snapshot of the touched entry.
     *
     * <p>{@code reason} is fixed to {@code "success"} and {@code data} starts empty; attach a
     * payload with {@link #withData}.
     *
     * @param operationType the operation that was applied
     * @param typeId        the targeted level type
     * @param oldLevel      level before the operation
     * @param newLevel      level after the operation
     * @param oldExp        progress before the operation
     * @param newExp        progress after the operation
     * @param amount        magnitude applied by the operation
     * @return a successful result with {@code reason} set to {@code "success"}
     */
    public static LevelOperationResult success(LevelOperationType operationType,
            String typeId,
            int oldLevel,
            int newLevel,
            double oldExp,
            double newExp,
            double amount) {
        return new LevelOperationResult(true, "success", operationType, typeId, oldLevel, newLevel, oldExp, newExp, amount, Map.of());
    }

    /**
     * Creates a failed result with zeroed snapshots.
     *
     * @param reason        stable machine-readable failure key
     * @param operationType the operation that was refused
     * @param typeId        the targeted level type
     * @return a failed result whose level, experience and amount components are {@code 0} and
     *         whose {@code data} map is empty
     */
    public static LevelOperationResult failure(String reason, LevelOperationType operationType, String typeId) {
        return new LevelOperationResult(false, reason, operationType, typeId, 0, 0, 0D, 0D, 0D, Map.of());
    }

    /**
     * Returns a copy carrying a different diagnostic payload; every other component is kept.
     *
     * @param values the payload to attach; {@code null} becomes an empty immutable map
     * @return a new result with {@code data} replaced
     */
    public LevelOperationResult withData(Map<String, Object> values) {
        return new LevelOperationResult(success, reason, operationType, typeId, oldLevel, newLevel, oldExp, newExp, amount, values);
    }
}
