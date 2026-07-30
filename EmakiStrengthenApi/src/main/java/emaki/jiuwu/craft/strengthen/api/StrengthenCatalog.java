package emaki.jiuwu.craft.strengthen.api;

import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.strengthen.api.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.api.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;

/**
 * Read-only queries against EmakiStrengthen's loaded recipes and item state.
 *
 * <p><strong>Thread:</strong> recipe-table methods may be called from any thread. Methods that inspect
 * an {@link ItemStack} must be called on the owner thread of the inventory, entity, or region holding
 * that stack. Player-scoped preview methods must be called on the player's entity-owner thread.
 */
@ApiStatus.NonExtendable
public interface StrengthenCatalog {

    /**
     * Reads the current strengthen state.
     *
     * <p>An item that is not eligible is still a successful state read whose
     * {@link StrengthenState#eligible()} is {@code false}. Only API unavailability, invalid input,
     * thread ownership, or an implementation failure produce an {@link EmakiResult.Failure}.
     *
     * @param itemStack the item to inspect
     * @return the resolved state
     */
    @NotNull
    EmakiResult<StrengthenState> readState(@Nullable ItemStack itemStack);

    /** {@return every loaded recipe in configured order; empty when unavailable} */
    @NotNull
    List<StrengthenRecipe> recipes();

    /**
     * Looks up one loaded recipe by id.
     *
     * @param recipeId the recipe id
     * @return the recipe when loaded, otherwise an empty optional
     */
    @NotNull
    Optional<StrengthenRecipe> recipe(@Nullable String recipeId);

    /**
     * Computes a non-committing attempt preview.
     *
     * <p>A business-rule rejection is represented by a successful {@link AttemptPreview} whose
     * {@link AttemptPreview#eligible()} is {@code false}, preserving its costs and diagnostic state.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread.
     *
     * @param player the player whose conditions and costs are evaluated
     * @param context the attempt inputs
     * @return the preview or an infrastructure/input failure
     */
    @NotNull
    EmakiResult<AttemptPreview> preview(@Nullable Player player, @Nullable AttemptContext context);

    /**
     * Resolves the success rate for an eligible preview.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread.
     *
     * @param player the player whose conditions and costs are evaluated
     * @param context the attempt inputs
     * @return the percentage success rate, or the preview rejection reason
     */
    @NotNull
    EmakiResult<Double> successRate(@Nullable Player player, @Nullable AttemptContext context);
}
