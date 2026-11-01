package emaki.jiuwu.craft.station.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only snapshot of one entry in a station craft queue.
 *
 * <p>Built from a live queue entry for rendering: {@code remainingMillis} is computed at build
 * time, so a snapshot grows stale while the countdown continues. The compact constructor rejects
 * a {@code null} {@code recipeIdentity}, {@code recipeId}, {@code state} or {@code channel},
 * clamps {@code schemaVersion} to at least {@code 1} and {@code remainingMillis} at {@code 0},
 * and copies the two lists into immutable lists ({@code null} becomes empty). Instances are
 * immutable.
 *
 * @param index             position of the entry within the queue snapshot
 * @param schemaVersion     persistence schema version the entry was stored under; at least
 *                          {@code 1}
 * @param recipeIdentity    recipe identity recorded with the entry, falling back to
 *                          {@code recipeId} when none was stored
 * @param recipeId          id of the recipe this entry is crafting
 * @param batch             number of batches this entry produces
 * @param state             lifecycle state of the entry
 * @param channel           the material channel the submission took its materials from
 * @param remainingMillis   millis left before the entry completes; clamped at {@code 0}
 * @param durationMillis    duration of one batch in millis after station speed multipliers
 * @param consumedMaterials the material debits already paid for this entry; {@code null} becomes
 *                          empty
 * @param pendingOutputs    outputs still owed to the player; {@code null} becomes empty
 */
public record QueueEntryView(int index,
        int schemaVersion,
        @NotNull String recipeIdentity,
        @NotNull String recipeId,
        long batch,
        @NotNull QueueEntryState state,
        @NotNull MaterialChannel channel,
        long remainingMillis,
        long durationMillis,
        @NotNull List<ConsumedMaterial> consumedMaterials,
        @NotNull List<PendingOutput> pendingOutputs) {

    /**
     * Normalizes identity, version and progress fields and freezes both lists.
     *
     * @param index             position within the queue snapshot
     * @param schemaVersion     stored schema version, clamped to at least {@code 1}
     * @param recipeIdentity    recorded recipe identity
     * @param recipeId          recipe id being crafted
     * @param batch             batch count
     * @param state             lifecycle state
     * @param channel           material channel taken
     * @param remainingMillis   millis left, clamped at {@code 0}
     * @param durationMillis    per-batch duration in millis
     * @param consumedMaterials paid material debits; {@code null} becomes empty
     * @param pendingOutputs    outputs still owed; {@code null} becomes empty
     * @throws NullPointerException     when {@code recipeIdentity}, {@code recipeId},
     *                                  {@code state} or {@code channel} is {@code null}
     */
    public QueueEntryView {
        if (recipeIdentity == null) {
            throw new NullPointerException("recipeIdentity");
        }
        if (recipeId == null) {
            throw new NullPointerException("recipeId");
        }
        if (state == null) {
            throw new NullPointerException("state");
        }
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        schemaVersion = Math.max(1, schemaVersion);
        remainingMillis = Math.max(0L, remainingMillis);
        consumedMaterials = consumedMaterials == null ? List.of() : List.copyOf(consumedMaterials);
        pendingOutputs = pendingOutputs == null ? List.of() : List.copyOf(pendingOutputs);
    }

    /**
     * Creates a view for a live entry with schema version {@code 1} and {@code recipeIdentity}
     * set to {@code recipeId}.
     *
     * @param index             position within the queue snapshot
     * @param recipeId          recipe id being crafted
     * @param batch             batch count
     * @param state             lifecycle state
     * @param channel           material channel taken
     * @param remainingMillis   millis left
     * @param durationMillis    per-batch duration in millis
     * @param consumedMaterials paid material debits; {@code null} becomes empty
     * @param pendingOutputs    outputs still owed; {@code null} becomes empty
     * @throws NullPointerException     when {@code recipeId}, {@code state} or {@code channel}
     *                                  is {@code null}
     */
    public QueueEntryView(int index,
            String recipeId,
            long batch,
            QueueEntryState state,
            MaterialChannel channel,
            long remainingMillis,
            long durationMillis,
            List<ConsumedMaterial> consumedMaterials,
            List<PendingOutput> pendingOutputs) {
        this(index, 1, recipeId, recipeId, batch, state, channel, remainingMillis, durationMillis,
                consumedMaterials, pendingOutputs);
    }

    /** {@return whether the craft finished but its outputs still wait for a manual claim} */
    public boolean awaitingClaim() {
        return state == QueueEntryState.PENDING_CLAIM;
    }
}
