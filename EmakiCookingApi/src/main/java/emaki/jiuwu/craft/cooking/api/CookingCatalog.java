package emaki.jiuwu.craft.cooking.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.cooking.api.model.CookingRecipeView;
import emaki.jiuwu.craft.cooking.api.model.CookingStationType;
import emaki.jiuwu.craft.cooking.api.model.CookingStationView;

/**
 * Read-only queries against EmakiCooking's recipes and placed stations.
 *
 * <p>Reached through {@code EmakiCookingApi.catalog()}.
 *
 * <h2>Threading</h2>
 * {@link #wokRecipes()} and {@link #recentStation(UUID)} may be called from any thread.
 * {@link #findRecipe} evaluates permissions and conditions against a live player and must run on that
 * player's owner thread. {@link #stationAt} reads placed-station state and must run on the owner thread
 * of that location's region.
 */
@ApiStatus.NonExtendable
public interface CookingCatalog {

    /**
     * Finds the recipe a station kind would run for a given input, as seen by one player.
     *
     * <p>Recipes can be gated by permission and by conditions, so the same input can resolve differently
     * for different players. Pass the player who is actually operating the station.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param stationType the station kind to search
     * @param inputSource item source shorthand of the input, such as {@code minecraft-potato}
     * @param player      the player whose permissions and conditions apply; may be {@code null} to
     *                    ignore player-specific gating
     * @return the matching recipe, or an empty optional when nothing matches
     */
    @NotNull
    Optional<CookingRecipeView> findRecipe(@Nullable CookingStationType stationType,
                                           @Nullable String inputSource,
                                           @Nullable Player player);

    /**
     * {@return every wok recipe; the wok is the one station whose recipes are matched by ingredient
     * combination rather than by a single input, so its table is exposed in full}
     */
    @NotNull
    List<CookingRecipeView> wokRecipes();

    /**
     * Reads the live state of a placed station.
     *
     * <p><strong>Thread:</strong> the owner thread of the location's region.
     *
     * @param location the block location of the station
     * @return the station snapshot, or an empty optional when no station stands there
     */
    @NotNull
    Optional<CookingStationView> stationAt(@Nullable Location location);

    /**
     * Returns the station a player most recently interacted with.
     *
     * <p>Backed by EmakiCooking's interaction tracker, which is populated from
     * {@code CookingStationInteractEvent} and cleared when the player quits. A player who has not touched
     * a station this session yields an empty optional.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param playerId the player's unique id
     * @return the station kind and its location, or an empty optional
     */
    @NotNull
    Optional<RecentStation> recentStation(@Nullable UUID playerId);

    /**
     * The station a player last interacted with.
     *
     * @param stationType the station kind
     * @param location    the station's block location
     */
    record RecentStation(@NotNull CookingStationType stationType, @NotNull Location location) {

        /**
         * Requires both components.
         *
         * @param stationType station kind
         * @param location    station location
         * @throws NullPointerException when either component is {@code null}
         */
        public RecentStation {
            if (stationType == null) {
                throw new NullPointerException("stationType");
            }
            if (location == null) {
                throw new NullPointerException("location");
            }
        }
    }
}
