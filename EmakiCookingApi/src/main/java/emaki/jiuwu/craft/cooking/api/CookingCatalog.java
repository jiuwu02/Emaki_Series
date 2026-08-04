package emaki.jiuwu.craft.cooking.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.cooking.api.model.CookingRecipeView;
import emaki.jiuwu.craft.cooking.api.model.CookingStationType;
import emaki.jiuwu.craft.cooking.api.model.CookingStationView;

/**
 * Read-only recipe and placed-station queries.
 *
 * <p>Reached through {@link EmakiCookingApi#catalog()}.
 */
@ApiStatus.NonExtendable
public interface CookingCatalog {

    /**
     * Lists every loaded recipe for one station kind, sorted by recipe id.
     *
     * <p>Returns an empty list rather than {@code null} when the station kind is {@code null} or the
     * runtime is unavailable. Recipes that carry a blank id are omitted.
     *
     * @param stationType station kind
     * @return all loaded recipes for exactly that station kind
     */
    @NotNull
    List<CookingRecipeView> recipes(@Nullable CookingStationType stationType);

    /**
     * Looks up one recipe by id within a single station's loader.
     *
     * <p>The id is matched case-insensitively; ids from another station kind are not consulted.
     *
     * @param stationType station kind
     * @param recipeId   recipe id
     * @return the recipe, or an empty optional when it does not exist in that station's loader
     */
    @NotNull
    Optional<CookingRecipeView> recipe(@Nullable CookingStationType stationType, @Nullable String recipeId);

    /**
     * Matches a single-input recipe.
     *
     * <p><strong>Thread:</strong> the player entity-owner thread when {@code player} is non-null, because
     * recipe availability can inspect permissions and conditions. Wok and fermentation recipes use
     * multi-input state and therefore return {@code REJECTED} rather than pretending a single item can
     * be matched.
     *
     * @param stationType station kind
     * @param input       input item
     * @param player      optional player used by permission and condition checks
     * @return the matching recipe
     */
    @NotNull
    EmakiResult<CookingRecipeView> matchRecipe(@Nullable CookingStationType stationType,
                                               @Nullable ItemStack input,
                                               @Nullable Player player);

    /**
     * Reads any of the seven station snapshots at a block location.
     *
     * <p><strong>Thread:</strong> the location-owner thread. Unknown locations return
     * {@code NOT_FOUND}; they are never reinterpreted as a wok.
     *
     * @param location block location to inspect
     * @return the station snapshot
     */
    @NotNull
    EmakiResult<CookingStationView> stationAt(@Nullable Location location);

    /**
     * Returns the location most recently reported through
     * {@link emaki.jiuwu.craft.cooking.api.event.CookingStationInteractEvent} for a player.
     *
     * @param playerId player id
     * @return recent station block centre, or empty when none is tracked
     */
    @NotNull
    Optional<Location> recentStation(@Nullable UUID playerId);
}
