package emaki.jiuwu.craft.station.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only view of one queued craft.
 *
 * <p>A detached snapshot: the values were true when the view was built and are never updated in
 * place. {@code remainingMillis} in particular is a derived value, so a caller that needs a live
 * countdown must re-read rather than hold on to one view.
 *
 * @param index            zero-based position in the station's queue at snapshot time
 * @param recipeId         the recipe being crafted
 * @param batch            how many times the recipe is applied by this entry; at least 1
 * @param state            lifecycle state at snapshot time
 * @param channel          the channel the materials were taken from
 * @param remainingMillis  milliseconds still to run; zero once the entry is due or finished
 * @param durationMillis   the entry's total configured duration
 * @param consumedMaterials the materials already debited for this entry
 * @param pendingOutputs   outputs still owed; empty unless the state is
 *        {@link QueueEntryState#PENDING_CLAIM}
 */
public record QueueEntryView(int index,
        @NotNull String recipeId,
        long batch,
        @NotNull QueueEntryState state,
        @NotNull MaterialChannel channel,
        long remainingMillis,
        long durationMillis,
        @NotNull List<ConsumedMaterial> consumedMaterials,
        @NotNull List<PendingOutput> pendingOutputs) {

    /**
     * Creates a queue-entry view with defensively copied collections.
     *
     * @param index            zero-based position in the station's queue
     * @param recipeId         the recipe being crafted
     * @param batch            how many times the recipe is applied
     * @param state            lifecycle state
     * @param channel          the channel the materials were taken from
     * @param remainingMillis  milliseconds still to run; negative values are clamped to zero
     * @param durationMillis   the entry's total configured duration
     * @param consumedMaterials the materials already debited; {@code null} becomes empty
     * @param pendingOutputs   outputs still owed; {@code null} becomes empty
     * @throws NullPointerException when {@code recipeId}, {@code state}, or {@code channel} is
     *         {@code null}
     */
    public QueueEntryView {
        if (recipeId == null) {
            throw new NullPointerException("recipeId");
        }
        if (state == null) {
            throw new NullPointerException("state");
        }
        if (channel == null) {
            throw new NullPointerException("channel");
        }
        remainingMillis = Math.max(0L, remainingMillis);
        consumedMaterials = consumedMaterials == null ? List.of() : List.copyOf(consumedMaterials);
        pendingOutputs = pendingOutputs == null ? List.of() : List.copyOf(pendingOutputs);
    }

    /** {@return whether this entry is waiting for the player to claim its outputs} */
    public boolean awaitingClaim() {
        return state == QueueEntryState.PENDING_CLAIM;
    }
}
