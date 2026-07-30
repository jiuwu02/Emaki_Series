package emaki.jiuwu.craft.cooking.api;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.cooking.api.model.CookingStationType;

/**
 * Cooking output construction and recipe completion checks.
 *
 * <p>Reached through {@link EmakiCookingApi#operations()}.
 */
@ApiStatus.NonExtendable
public interface CookingOperations {

    /**
     * Creates detached success-output items for one recipe.
     *
     * <p><strong>Thread:</strong> any thread that may safely construct detached Bukkit items. Chance
     * filters are intentionally not rolled here: every configured success output is constructed once.
     * A mixture of constructed and invalid outputs returns {@link EmakiResult.Partial}.
     *
     * @param stationType station kind owning the recipe
     * @param recipeId   recipe id
     * @return constructed output items
     */
    @NotNull
    EmakiResult<List<ItemStack>> createOutputs(@Nullable CookingStationType stationType,
                                               @Nullable String recipeId);

    /**
     * Evaluates a recipe's configured completion condition.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread. Because the delivery signature does
     * not include a station type, duplicate recipe ids across station loaders are rejected as ambiguous.
     * A returned {@code false} is a legitimate business value, distinct from API unavailability.
     *
     * @param recipeId recipe id, unique across all seven station loaders
     * @param player   player used by placeholders and conditions
     * @return whether the condition passes
     */
    @NotNull
    EmakiResult<Boolean> completionConditionPasses(@Nullable String recipeId, @Nullable Player player);
}
