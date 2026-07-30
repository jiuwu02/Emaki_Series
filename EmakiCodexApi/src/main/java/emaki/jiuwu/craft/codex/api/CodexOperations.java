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
     */
    @NotNull EmakiResult<Unit> grant(@Nullable UUID playerId, @Nullable String advancementId);

    /** Fires cancellable {@code AdvancementRevokeEvent} before revoking the criterion. */
    @NotNull EmakiResult<Unit> revoke(@Nullable UUID playerId, @Nullable String advancementId);
}
