package emaki.jiuwu.craft.strengthen.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.strengthen.api.model.ItemMasteryView;

/**
 * Read-only access to mastery state owned by an enhancement target provider.
 *
 * <p>This contract only exposes snapshots. It does not mutate experience, levels, milestones, or item
 * identity, and it does not define mastery change events. Providers that do not yet expose mastery return
 * {@link EmakiResult#unavailable()} rather than fabricating an empty or zero-valued state.
 *
 * <p><strong>Thread:</strong> call on the owner thread of the inventory, entity, or region holding the
 * stack. A detached item copy may be queried on the caller's current thread. The player is optional context
 * for providers whose target selection is player-scoped.
 */
@ApiStatus.NonExtendable
public interface ItemMastery {

    /**
     * Reads the current immutable mastery snapshot for one item.
     *
     * @param itemStack the item to inspect
     * @return the provider-owned snapshot, or a classified failure when unsupported or unavailable
     */
    default @NotNull EmakiResult<ItemMasteryView> snapshot(@Nullable ItemStack itemStack) {
        return snapshot(null, itemStack);
    }

    /**
     * Reads the current immutable mastery snapshot with optional player context.
     *
     * @param player    the acting player when the target provider requires player context
     * @param itemStack the item to inspect
     * @return the provider-owned snapshot, or a classified failure when unsupported or unavailable
     */
    @NotNull
    EmakiResult<ItemMasteryView> snapshot(@Nullable Player player, @Nullable ItemStack itemStack);
}
