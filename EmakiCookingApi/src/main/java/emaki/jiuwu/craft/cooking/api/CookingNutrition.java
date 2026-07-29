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

/**
 * The player nutrition subsystem, reached through {@code EmakiCookingApi.nutrition()}.
 *
 * <p>Nutrition is a set of named values per player, each with its own configured range and default.
 * Server owners define the types; this API reads and writes values against them.
 *
 * <h2>Why reads return a result rather than a bare double</h2>
 * EmakiCooking's internal read collapses three distinct situations onto the number {@code 0}: an unknown
 * type id, a player with no loaded data, and a genuine stored value of zero. A caller cannot tell a
 * misspelled type from a starving player. This API separates them: an unknown type is
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#NOT_FOUND}, an unloaded player yields the
 * type's configured default as a {@code Partial}, and a real reading is a plain success.
 *
 * <h2>When the subsystem is switched off</h2>
 * A server owner may disable nutrition while cooking stations keep working, so
 * {@code EmakiCookingApi.status().ready()} does not imply nutrition is on. Check {@link #enabled()}
 * first, or branch on {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#DISABLED}, which every
 * method here returns in that state. {@code DISABLED} is constant until the configuration changes, so
 * hide the feature rather than retrying.
 *
 * <h2>Threading</h2>
 * {@link #enabled()}, {@link #types()}, {@link #type(String)}, and {@link #value(UUID, String)} may be
 * called from any thread. The mutating methods and {@link #applyFood} touch player state and must be
 * called on the owner thread of the affected player when that player is online.
 */
@ApiStatus.NonExtendable
public interface CookingNutrition {

    /** {@return whether the nutrition subsystem is enabled in configuration} */
    boolean enabled();

    /** {@return every configured nutrition type, in configuration order; empty when disabled} */
    @NotNull
    List<NutritionTypeView> types();

    /**
     * Looks up one nutrition type by id.
     *
     * @param typeId the type id, case-insensitive
     * @return the type when configured, otherwise an empty optional
     */
    @NotNull
    Optional<NutritionTypeView> type(@Nullable String typeId);

    /**
     * Reads a player's current value for one nutrition type.
     *
     * <p>Returns a {@code Partial} carrying the type's configured default when the player has no loaded
     * data, which distinguishes "not tracked yet" from "tracked and equal to the default".
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param playerId the player's unique id
     * @param typeId   the nutrition type id
     * @return the current value, or a failure when the type is unknown
     */
    @NotNull
    EmakiResult<Double> value(@Nullable UUID playerId, @Nullable String typeId);

    /**
     * Adds to a player's nutrition value, clamping into the type's configured range.
     *
     * <p><strong>Thread:</strong> the affected player's owner thread when online.
     *
     * @param playerId the player's unique id
     * @param typeId   the nutrition type id
     * @param amount   how much to add; negative values subtract
     * @return the resulting change, or a failure when the type is unknown or the data is unavailable
     */
    @NotNull
    EmakiResult<NutritionChange> add(@Nullable UUID playerId, @Nullable String typeId, double amount);

    /**
     * Sets a player's nutrition value, clamping into the type's configured range.
     *
     * <p><strong>Thread:</strong> the affected player's owner thread when online.
     *
     * @param playerId the player's unique id
     * @param typeId   the nutrition type id
     * @param amount   the value to set
     * @return the resulting change, or a failure when the type is unknown or the data is unavailable
     */
    @NotNull
    EmakiResult<NutritionChange> set(@Nullable UUID playerId, @Nullable String typeId, double amount);

    /**
     * Applies the nutrition effects configured for a food item, as if the player had eaten it.
     *
     * <p>Fires {@code PlayerNutritionConsumeEvent} first; a cancelled event yields a
     * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#REJECTED} failure. A food item with no
     * matching nutrition rule also yields {@code REJECTED}, since nothing was applied. When the
     * subsystem is switched off the failure is
     * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#DISABLED}.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player    the player consuming the item
     * @param itemStack the food item
     * @return success when at least one rule applied, otherwise a failure
     */
    @NotNull
    EmakiResult<Unit> applyFood(@Nullable Player player, @Nullable ItemStack itemStack);

    /**
     * Re-evaluates a player's nutrition thresholds, firing
     * {@code NutritionThresholdChangeEvent} for any that changed state.
     *
     * <p>Use this after adjusting values through a path EmakiCooking cannot observe.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player the player to re-evaluate
     * @return success when evaluation ran, or a failure when the subsystem is disabled or the player has
     *         no loaded data
     */
    @NotNull
    EmakiResult<Unit> recheckThresholds(@Nullable Player player);
}
