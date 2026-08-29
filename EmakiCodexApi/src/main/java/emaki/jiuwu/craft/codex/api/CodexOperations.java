package emaki.jiuwu.craft.codex.api;

import java.util.UUID;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;

/** Player advancement mutations. Both methods require the target player's owner thread. */
@ApiStatus.NonExtendable
public interface CodexOperations {

    /**
     * Grants a registered advancement. Fires cancellable {@code AdvancementGrantEvent} before mutation.
     * Completion is reported by {@code AdvancementCompletedEvent} from the actual Bukkit completion event.
     *
     * <p><strong>Thread:</strong> the target player's owner thread; the call is never rescheduled. Shared
     * validation and availability failures follow {@link EmakiResult}. An already-completed advancement is a
     * rejected operation, and cancellation leaves progress unchanged.
     *
     * @param playerId      the target player's unique id, resolved against online players only
     * @param advancementId the registered advancement id
     * @return success once the criterion was awarded, or a classified failure
     */
    @NotNull EmakiResult<Unit> grant(@Nullable UUID playerId, @Nullable String advancementId);

    /**
     * Revokes a registered advancement, firing cancellable {@code AdvancementRevokeEvent} before removing
     * the criterion.
     *
     * <p><strong>Thread:</strong> the target player's owner thread, with the same non-rescheduling and shared
     * failure contract as {@link #grant}. Revoking an incomplete advancement is rejected rather than treated
     * as an idempotent success; cancellation leaves progress unchanged.
     *
     * @param playerId      the target player's unique id, resolved against online players only
     * @param advancementId the registered advancement id
     * @return success once the criterion was revoked, or a classified failure
     */
    @NotNull EmakiResult<Unit> revoke(@Nullable UUID playerId, @Nullable String advancementId);
}
