package emaki.jiuwu.craft.forge.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;

/**
 * State-changing forging operations.
 *
 * <p>Reached through {@code EmakiForgeApi.operations()}.
 *
 * <p><strong>Thread:</strong> every method here touches a player, an inventory, or an item and must be
 * called on the owner thread of that object. On Folia that is the entity's region thread. Calls from
 * the wrong thread report {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD}
 * rather than corrupting state; use
 * {@link emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling#runForEntity} to hop.
 *
 * <h2>No programmatic forge entry point</h2>
 * EmakiForge's forge execution requires a prepared attempt, a runtime generation token, and delivery
 * claim callbacks that only its GUI session layer can supply; its two events
 * ({@code ForgeStartEvent}, {@code ForgeCompletedEvent}) are fired exclusively from that GUI path.
 * Exposing a raw execution call would therefore produce attempts that bypass both the session
 * lifecycle and the event contract. Drive forging through {@link #openForgeGui(Player, String)}
 * instead.
 */
@ApiStatus.NonExtendable
public interface ForgeOperations {

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
     * @param player the player to test
     * @return whether the player currently has the recipe book open
     */
    boolean viewingRecipeBook(@Nullable Player player);

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
