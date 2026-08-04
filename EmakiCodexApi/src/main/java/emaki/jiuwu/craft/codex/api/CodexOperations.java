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
     * <p><strong>Thread:</strong> the target player's entity-owner thread. Calling elsewhere returns
     * {@code WRONG_THREAD} without granting anything; the call is never rescheduled for you.
     *
     * <p>Failure branches: {@code INVALID_INPUT} for a {@code null} player id or a blank advancement id,
     * {@code TARGET_OFFLINE} when the id resolves to no online player, {@code UNAVAILABLE} when EmakiCodex
     * is disabled or its registrar is not built, {@code REJECTED} when the advancement feature is switched
     * off in config, when the player has already completed it, or when Bukkit refuses the criterion award,
     * {@code NOT_FOUND} when the id is not registered or the resolved key has no advancement on this
     * server, and {@code CANCELLED} when a listener cancels the grant event.
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
     * <p><strong>Thread:</strong> the target player's entity-owner thread, with the same
     * {@code WRONG_THREAD} behaviour as {@link #grant}.
     *
     * <p>Shares {@link #grant}'s validation and failure branches, except that the pre-state check is
     * inverted: revoking an advancement the player has not completed is {@code REJECTED} rather than a
     * silent success.
     *
     * @param playerId      the target player's unique id, resolved against online players only
     * @param advancementId the registered advancement id
     * @return success once the criterion was revoked, or a classified failure
     */
    @NotNull EmakiResult<Unit> revoke(@Nullable UUID playerId, @Nullable String advancementId);
}
