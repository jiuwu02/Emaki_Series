package emaki.jiuwu.craft.forge.api;

import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.forge.api.model.ForgeInputs;
import emaki.jiuwu.craft.forge.api.model.ForgeOutcome;

/**
 * State-changing forging operations.
 *
 * <p>Reached through {@code EmakiForgeApi.operations()}.
 *
 * <p><strong>Thread:</strong> synchronous GUI and inventory methods must be called on the player's
 * owner thread. {@link #forgeAsync(Player, String, ForgeInputs)} is the exception: it accepts calls
 * from any thread and dispatches every player/Bukkit phase onto the player's owner thread, including
 * on Folia.
 *

 * {@code ForgeInputs} is a detached snapshot of items the caller has already reserved outside the
 * player's inventory. EmakiForge validates that snapshot through the same runtime execution path as
 * the GUI, but it does not search for or remove matching stacks from arbitrary inventory slots. The
 * caller remains responsible for committing its physical escrow on success or releasing it on
 * failure; do not leave the same physical items available to the player while the future is running.
 */
@ApiStatus.NonExtendable
public interface ForgeOperations {

    /**
     * Executes one forge attempt through EmakiForge's real preparation, validation, action, quality,
     * delivery, history, and event pipeline.
     *
     * <p>The method may be called from any thread. Completion is asynchronous; Bukkit event delivery
     * and result mapping happen on the player's owner thread. Cancelling the returned future does not
     * roll back an attempt that has already crossed the runtime delivery commit boundary.
     *
     * @param player   online player receiving the result
     * @param recipeId recipe to execute
     * @param inputs   detached input escrow snapshot
     * @return a future carrying the committed outcome or a classified failure
     */
    @ApiStatus.Experimental
    @NotNull
    CompletableFuture<EmakiResult<ForgeOutcome>> forgeAsync(@Nullable Player player,
                                                             @Nullable String recipeId,
                                                             @Nullable ForgeInputs inputs);

    /**
     * Opens the forging GUI focused on one recipe.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player   the player to show the GUI to
     * @param recipeId the recipe to focus
     * @return success, or a failure describing why the GUI did not open
     */
    @NotNull
    EmakiResult<Unit> openForgeGui(@Nullable Player player, @Nullable String recipeId);

    /**
     * Opens the general forging GUI with no recipe preselected.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player the player to show the GUI to
     * @return success, or a failure describing why the GUI did not open
     */
    @NotNull
    EmakiResult<Unit> openForgeGui(@Nullable Player player);

    /**
     * Opens the recipe book.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player the player to show the book to
     * @param page   zero-based page index; negative values are treated as {@code 0}
     * @return success, or a failure describing why the book did not open
     */
    @NotNull
    EmakiResult<Unit> openRecipeBook(@Nullable Player player, int page);

    /**
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player the player to test
     * @return whether the player currently has the recipe book open, without collapsing unavailable
     *         into the legitimate business value {@code false}
     */
    @NotNull
    EmakiResult<Boolean> viewingRecipeBook(@Nullable Player player);

    /**
     * Rebuilds a forged item against the current recipe and material definitions.
     *
     * <p>Use this after a configuration reload so existing items pick up new stats. Items that carry
     * no EmakiForge data are returned unchanged as a {@code Partial} result, which distinguishes
     * "nothing to do" from "refreshed".
     *
     * <p><strong>Thread:</strong> the owner thread of whatever holds the stack.
     *
     * @param itemStack the stack to refresh
     * @return the refreshed stack, or a failure describing why it could not be refreshed
     */
    @NotNull
    EmakiResult<ItemStack> refreshItem(@Nullable ItemStack itemStack);

    /**
     * Rebuilds every forged item in a player's inventory, armour slots, off-hand, and cursor.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player the player whose inventory is refreshed
     * @return success, or a failure describing why the refresh did not run
     */
    @NotNull
    EmakiResult<Unit> refreshPlayer(@Nullable Player player);
}
