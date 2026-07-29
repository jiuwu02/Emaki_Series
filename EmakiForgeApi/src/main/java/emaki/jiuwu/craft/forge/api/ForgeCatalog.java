package emaki.jiuwu.craft.forge.api;

import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.forge.api.model.ForgeInputs;
import emaki.jiuwu.craft.forge.api.model.ForgeMaterialView;
import emaki.jiuwu.craft.forge.api.model.ForgeRecipeView;
import emaki.jiuwu.craft.forge.api.model.ForgeValidation;

/**
 * Read-only queries against EmakiForge's recipe and material tables.
 *
 * <p>Reached through {@code EmakiForgeApi.catalog()}. Every method is cheap and side-effect free.
 *
 * <p><strong>Thread:</strong> {@link #recipes()}, {@link #recipe(String)}, {@link #materialById(String)},
 * and {@link #materialByItem(ItemStack)} may be called from any thread. The player-scoped methods
 * {@link #matchRecipe} and {@link #validate} evaluate permissions and conditions against a live
 * player and must be called on that player's owner thread; they report
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD} otherwise.
 */
@ApiStatus.NonExtendable
public interface ForgeCatalog {

    /** {@return every loaded recipe in recipe-book order; empty when EmakiForge is unavailable} */
    @NotNull
    List<ForgeRecipeView> recipes();

    /**
     * Looks up one recipe by id. The id is normalised with {@code Locale.ROOT}.
     *
     * @param recipeId the recipe id
     * @return the recipe when loaded, otherwise an empty optional
     */
    @NotNull
    Optional<ForgeRecipeView> recipe(@Nullable String recipeId);

    /**
     * Looks up a forging material definition by its id.
     *
     * @param materialId the material id
     * @return the material when defined, otherwise an empty optional
     */
    @NotNull
    Optional<ForgeMaterialView> materialById(@Nullable String materialId);

    /**
     * Looks up a forging material definition by a concrete item stack.
     *
     * @param itemStack the stack to identify
     * @return the material when the stack matches a definition, otherwise an empty optional
     */
    @NotNull
    Optional<ForgeMaterialView> materialByItem(@Nullable ItemStack itemStack);

    /**
     * Finds the recipe that the given item layout would produce for this player.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player the player whose permissions and conditions are evaluated
     * @param inputs the item layout to match
     * @return the matched recipe, or a {@code REJECTED} failure whose reason key explains why no
     *         recipe matched
     */
    @NotNull
    EmakiResult<ForgeRecipeView> matchRecipe(@Nullable Player player, @Nullable ForgeInputs inputs);

    /**
     * Checks whether the player may forge the given recipe with the given item layout.
     *
     * <p>A business rejection is returned as a <em>successful</em> result whose
     * {@link ForgeValidation#allowed()} is {@code false}; only unavailability, an unknown recipe id,
     * or a thread violation produce a failure.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player   the player attempting the forge
     * @param recipeId the recipe id
     * @param inputs   the item layout to validate
     * @return the validation outcome
     */
    @NotNull
    EmakiResult<ForgeValidation> validate(@Nullable Player player,
                                          @Nullable String recipeId,
                                          @Nullable ForgeInputs inputs);

    /**
     * {@return whether the forging subsystem is currently accepting new attempts; {@code false}
     * during shutdown drain or while EmakiForge is unavailable}
     */
    boolean accepting();
}
