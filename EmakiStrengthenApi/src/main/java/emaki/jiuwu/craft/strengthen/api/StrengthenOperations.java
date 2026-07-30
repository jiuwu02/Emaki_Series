package emaki.jiuwu.craft.strengthen.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.strengthen.api.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.api.model.AttemptResult;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenTransferOutcome;

/**
 * State-changing EmakiStrengthen operations.
 *
 * <p><strong>Thread:</strong> player-scoped methods must be called on that player's entity-owner
 * thread. Item-only methods must be called on the owner thread of the inventory, entity, or region
 * holding the stack. Detached item copies may be processed on the caller's current thread.
 */
@ApiStatus.NonExtendable
public interface StrengthenOperations {

    /**
     * Performs one strengthen attempt using cloned input data.
     *
     * <p>A committed failed roll is a successful API operation carrying an {@link AttemptResult}
     * whose {@link AttemptResult#success()} is {@code false}. Early validation, cancellation, charge,
     * or rebuild failures are returned as {@link EmakiResult.Failure} values.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread.
     */
    @NotNull
    EmakiResult<AttemptResult> attempt(@Nullable Player player, @Nullable AttemptContext context);

    /**
     * Transfers strengthened stars from a source item to a target item.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread.
     */
    @NotNull
    EmakiResult<StrengthenTransferOutcome> transfer(@Nullable Player player,
            @Nullable ItemStack source,
            @Nullable ItemStack target,
            double decayRate);

    /**
     * Rebuilds the strengthen layer from stored state.
     *
     * <p>An item with no strengthen layer returns a partial result rather than being
     * indistinguishable from an unavailable API.
     */
    @NotNull
    EmakiResult<ItemStack> rebuild(@Nullable ItemStack itemStack);

    /**
     * Opens the strengthen GUI.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread.
     */
    @NotNull
    EmakiResult<Unit> openGui(@Nullable Player player);

    /** Rebuilds one item's strengthen layer against the current definitions. */
    @NotNull
    EmakiResult<ItemStack> refreshItem(@Nullable ItemStack itemStack);

    /**
     * Refreshes strengthened items in a player's inventory, armour, and off-hand.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread.
     *
     * @return the number of inventory slots whose item was rebuilt
     */
    @NotNull
    EmakiResult<Integer> refreshPlayer(@Nullable Player player);
}
