package emaki.jiuwu.craft.level.api;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;

/** Synchronous state-changing level operations. */
@ApiStatus.NonExtendable
public interface LevelOperations {

    /** <strong>Thread:</strong> the target player's owner thread. */
    @NotNull
    EmakiResult<LevelOperationResult> addExp(@Nullable UUID uuid,
            @Nullable String typeId,
            double amount,
            @Nullable String reason);

    /** <strong>Thread:</strong> the target player's owner thread. */
    @NotNull
    EmakiResult<LevelOperationResult> addExp(@Nullable UUID uuid,
            @Nullable String typeId,
            double amount,
            @Nullable String reason,
            boolean silent);

    /** <strong>Thread:</strong> the target player's owner thread. */
    @NotNull
    EmakiResult<LevelOperationResult> removeExp(@Nullable UUID uuid,
            @Nullable String typeId,
            double amount,
            @Nullable String reason);

    /** <strong>Thread:</strong> the target player's owner thread. */
    @NotNull
    EmakiResult<LevelOperationResult> setExp(@Nullable UUID uuid,
            @Nullable String typeId,
            double amount,
            @Nullable String reason);

    /** <strong>Thread:</strong> the target player's owner thread. */
    @NotNull
    EmakiResult<LevelOperationResult> addLevel(@Nullable UUID uuid,
            @Nullable String typeId,
            int amount,
            @Nullable String reason);

    /** <strong>Thread:</strong> the target player's owner thread. */
    @NotNull
    EmakiResult<LevelOperationResult> removeLevel(@Nullable UUID uuid,
            @Nullable String typeId,
            int amount,
            @Nullable String reason);

    /** <strong>Thread:</strong> the target player's owner thread. */
    @NotNull
    EmakiResult<LevelOperationResult> setLevel(@Nullable UUID uuid,
            @Nullable String typeId,
            int level,
            @Nullable String reason);

    /** <strong>Thread:</strong> the target player's owner thread. */
    @NotNull
    EmakiResult<LevelOperationResult> levelUp(@Nullable UUID uuid,
            @Nullable String typeId,
            @Nullable LevelUpCause cause);

    /** <strong>Thread:</strong> the target player's owner thread. */
    @NotNull
    EmakiResult<LevelOperationResult> reset(@Nullable UUID uuid, @Nullable String typeId);

    /** <strong>Thread:</strong> the player's owner thread. */
    @NotNull
    EmakiResult<Unit> syncPlayer(@Nullable Player player);

    /** <strong>Thread:</strong> the player's owner thread. */
    @NotNull
    EmakiResult<Unit> openGui(@Nullable Player player, @Nullable String typeId);

    /** <strong>Thread:</strong> the player's owner thread. */
    @NotNull
    EmakiResult<Unit> openTopGui(@Nullable Player player, @Nullable String typeId);
}
