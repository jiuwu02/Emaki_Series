package emaki.jiuwu.craft.item.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.item.api.model.RepairOutcome;
import emaki.jiuwu.craft.item.api.model.RepairQuoteView;

/**
 * Durability-disable state and economy-backed repair operations.
 *
 * <p>The public quote/repair pair bridges the runtime economy repair service because that is the repair
 * path whose complete input is {@code Player + ItemStack}. Material repair remains a GUI/inventory
 * transaction and is not represented by a fabricated convenience method.
 *
 * <p>Player-scoped methods require the player's entity-owner thread. Detached-stack disable flags may be
 * read or changed on any thread; live inventory stacks must be accessed on their holder's owner thread.
 */
@ApiStatus.NonExtendable
public interface ItemRepair {

    /** {@return whether the runtime disabled flag is present on the stack} */
    boolean isDisabled(@Nullable ItemStack itemStack);

    /** Marks a stack disabled through the real repair service. */
    @NotNull
    EmakiResult<Unit> markDisabled(@Nullable ItemStack itemStack);

    /** Clears a stack's disabled flag through the real repair service. */
    @NotNull
    EmakiResult<Unit> clearDisabled(@Nullable ItemStack itemStack);

    /**
     * Quotes the configured economy repair costs.
     *
     * <p>An unaffordable quote is still a successful quote whose {@link RepairQuoteView#affordable()} is
     * {@code false}. A disabled repair feature is {@code REJECTED}.
     */
    @NotNull
    EmakiResult<RepairQuoteView> quote(@Nullable Player player, @Nullable ItemStack itemStack);

    /**
     * Executes the configured economy repair, including event, charge, compensation, durability commit,
     * and post-repair actions from the runtime service.
     */
    @NotNull
    EmakiResult<RepairOutcome> repair(@Nullable Player player, @Nullable ItemStack itemStack);
}
