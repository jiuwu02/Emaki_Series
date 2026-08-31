package emaki.jiuwu.craft.cooking.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.cooking.api.model.NutritionChange;
import emaki.jiuwu.craft.cooking.api.model.NutritionTypeView;

/** Public nutrition queries and mutations. */
@ApiStatus.NonExtendable
public interface CookingNutrition {

    /** {@return whether nutrition gameplay is enabled by server configuration} */
    boolean enabled();

    /**
     * Reads one nutrition value for a player, falling back to the type's configured default.
     *
     * <p>Validation, disabled-subsystem and availability failures use the shared {@link EmakiResult}
     * contract.
     *
     * @param playerId player id
     * @param typeId   nutrition type id
     * @return current or configured default value
     */
    @NotNull
    EmakiResult<Double> value(@Nullable UUID playerId, @Nullable String typeId);

    /** Adds an amount to one cached player value. */
    @NotNull
    EmakiResult<NutritionChange> add(@Nullable UUID playerId, @Nullable String typeId, double amount);

    /** Removes an amount from one cached player value. */
    @NotNull
    EmakiResult<NutritionChange> remove(@Nullable UUID playerId, @Nullable String typeId, double amount);

    /** Replaces one cached player value. */
    @NotNull
    EmakiResult<NutritionChange> set(@Nullable UUID playerId, @Nullable String typeId, double amount);

    /**
     * Applies all nutrition rules matching one food item.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread.
     */
    @NotNull
    EmakiResult<Unit> applyFood(@Nullable Player player, @Nullable ItemStack itemStack);

    /**
     * Re-evaluates configured thresholds for cached player data.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread.
     */
    @NotNull
    EmakiResult<Unit> recheckThresholds(@Nullable Player player);

    /** {@return all registered nutrition types in id order} */
    @NotNull
    List<NutritionTypeView> types();

    /**
     * Looks up one registered nutrition type definition.
     *
     * <p>Matching does not depend on whether nutrition gameplay is currently enabled; an empty optional
     * is returned for a blank id or when the type registry is unavailable.
     *
     * @param typeId nutrition type id
     * @return its definition, or empty when unknown
     */
    @NotNull
    Optional<NutritionTypeView> type(@Nullable String typeId);
}
