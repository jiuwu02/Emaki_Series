package emaki.jiuwu.craft.strengthen.api.model;

import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A read-only view of one enhancement transaction recorded in the operation journal.
 *
 * <p>An operation is journalled from the moment an attempt starts until the runtime decides the
 * entry may be pruned. Entries whose compensation is still outstanding are deliberately retained,
 * which makes this view the lookup path for a player who was handed an operation id together with
 * a {@code strengthen.error.compensation_pending} message.
 *
 * <p>The {@link #phase()} value is a runtime diagnostic string, not a stable enum: treat it as text
 * to display or log. Known values at the time of writing are {@code COMMITTED_SUCCESS},
 * {@code COMMITTED_FAILURE}, {@code COMPENSATION_PENDING} and {@code NOT_COMMITTED}, plus the
 * in-flight phase written when the attempt begins.
 *
 * @param operationId          the operation id the caller looked up, never blank
 * @param playerId             the player the operation was journalled for, or {@code null} when the
 *                             entry carries no player
 * @param phase                the recorded phase string; empty when the runtime stored none
 * @param compensationPending  whether this operation is still awaiting a refund; such entries are
 *                             not pruned automatically and are the ones worth escalating
 */
public record EnhancementOperationView(@NotNull String operationId,
        @Nullable UUID playerId,
        @NotNull String phase,
        boolean compensationPending) {

    /** Phase written for an operation whose compensation has not settled yet. */
    public static final String PHASE_COMPENSATION_PENDING = "COMPENSATION_PENDING";

    public EnhancementOperationView {
        if (operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("operationId cannot be null or blank");
        }
        phase = phase == null ? "" : phase;
    }
}
