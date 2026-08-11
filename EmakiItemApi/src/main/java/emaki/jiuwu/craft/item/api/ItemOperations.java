package emaki.jiuwu.craft.item.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.item.api.model.ItemRefreshSummary;

/**
 * Item creation, refresh, and repair-GUI operations.
 *
 * <p>{@link #create} must run on the global-region owner thread so the synchronous cancellable creation
 * event is always fired before the item is returned. Player-scoped methods require the player's
 * entity-owner thread. Detached-stack refreshes do not provide enough context for the bridge to verify an
 * owner; callers are responsible for invoking them on the owner of any live inventory holding the stack.
 */
@ApiStatus.NonExtendable
public interface ItemOperations {

    /**
     * Builds a fresh item and fires {@code EmakiItemCreateEvent} before returning it.
     *
     * @param id definition id or alias
     * @param amount requested amount; must be positive
     * @return the created stack, {@code CANCELLED} when a listener vetoes it, or another classified failure
     */
    @NotNull
    EmakiResult<ItemStack> create(@Nullable String id, int amount);

    /**
     * Refreshes a stack only when its update policy and revision require it.
     *
     * @param itemStack stack to refresh
     * @return the resulting stack; the original stack is a legitimate success when already current
     */
    @NotNull
    EmakiResult<ItemStack> refresh(@Nullable ItemStack itemStack);

    /**
     * Forces a rebuild through the runtime update service.
     *
     * @param itemStack stack to rebuild
     * @return the resulting stack; a disabled update policy is {@code REJECTED}
     */
    @NotNull
    EmakiResult<ItemStack> forceRefresh(@Nullable ItemStack itemStack);

    /**
     * Refreshes every relevant inventory slot for a player.
     *
     * <p>Compare-before-write conflicts produce {@link EmakiResult.Partial} carrying the actual summary.
     *
     * @param player player whose items are refreshed
     * @param trigger runtime trigger name, such as {@code command} or {@code join}
     * @return detailed refresh summary
     */
    @NotNull
    EmakiResult<ItemRefreshSummary> refreshPlayer(@Nullable Player player, @Nullable String trigger);

    /**
     * Recomputes and reapplies equipped item-set presentation and bonuses.
     *
     * <p>Compare-before-write conflicts produce {@link EmakiResult.Partial} carrying the actual summary.
     * A disabled set-bonus subsystem or trigger is {@code REJECTED} with a stable reason key.
     *
     * @param player player whose equipped sets are refreshed
     * @param trigger runtime trigger name
     * @return detailed refresh summary
     */
    @NotNull
    EmakiResult<ItemRefreshSummary> refreshEquippedSets(@Nullable Player player, @Nullable String trigger);

    /**
     * Opens the real EmakiItem repair GUI.
     *
     * @param player target player
     * @return success, or a classified failure when the GUI cannot open
     */
    @NotNull
    EmakiResult<Unit> openRepairGui(@Nullable Player player);
}
