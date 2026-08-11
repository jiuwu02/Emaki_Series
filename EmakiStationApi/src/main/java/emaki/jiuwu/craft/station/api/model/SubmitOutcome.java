package emaki.jiuwu.craft.station.api.model;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * What a successful submission produced.
 *
 * <p>A submission that entered the queue reports {@code queued = true} and the position it took. A
 * recipe with no duration settles immediately instead, reporting {@code queued = false} and a
 * position of {@code -1}; the outputs are then either already delivered or listed in
 * {@code pendingOutputs} when no destination could take them.
 *
 * @param recipeId       the recipe that was submitted
 * @param batch          how many times the recipe was applied
 * @param queued         whether the submission entered the timed queue
 * @param queueIndex     the position taken in the queue, or {@code -1} for an instant settle
 * @param consumed       the materials that were debited
 * @param pendingOutputs outputs that could not be delivered and now await a claim
 */
public record SubmitOutcome(@NotNull String recipeId,
        long batch,
        boolean queued,
        int queueIndex,
        @NotNull List<ConsumedMaterial> consumed,
        @NotNull List<PendingOutput> pendingOutputs) {

    /**
     * Creates a submit outcome with defensively copied collections.
     *
     * @param recipeId       the recipe that was submitted
     * @param batch          how many times the recipe was applied
     * @param queued         whether the submission entered the timed queue
     * @param queueIndex     the position taken, or {@code -1} for an instant settle
     * @param consumed       the materials that were debited; {@code null} becomes empty
     * @param pendingOutputs outputs awaiting a claim; {@code null} becomes empty
     * @throws NullPointerException when {@code recipeId} is {@code null}
     */
    public SubmitOutcome {
        if (recipeId == null) {
            throw new NullPointerException("recipeId");
        }
        consumed = consumed == null ? List.of() : List.copyOf(consumed);
        pendingOutputs = pendingOutputs == null ? List.of() : List.copyOf(pendingOutputs);
    }
}
