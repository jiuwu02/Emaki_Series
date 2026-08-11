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

    /**
     * Reads the runtime's repair-disabled marker straight off the stack's persistent data.
     *
     * <p>Answers {@code false} for a {@code null} or air stack, for a stack with no item meta, and when
     * EmakiItem's repair service is not installed. Because {@code false} therefore covers both "not
     * disabled" and "cannot tell", check {@link EmakiItemApi#status()} when the distinction matters.
     *
     * @param itemStack the stack to inspect; {@code null} or air yields {@code false}
     * @return whether the runtime disabled flag is present on the stack
     */
    boolean isDisabled(@Nullable ItemStack itemStack);

    /**
     * Marks a stack repair-disabled through the real repair service, writing EmakiItem's own persistent
     * data key on the given stack in place.
     *
     * <p>The stack must not be {@code null} or air, otherwise the call is {@code INVALID_INPUT}. When
     * the repair service is not installed the result is {@code UNAVAILABLE}. A write that does not
     * survive a read-back, and any runtime exception raised while committing, both surface as
     * {@code INTERNAL_ERROR} rather than being reported as success.
     *
     * <p>This method does not itself check thread ownership, so respect the interface-level rule: a
     * detached stack may be marked on any thread, a live inventory stack only on its holder's owner
     * thread.
     *
     * @param itemStack the stack to mark, mutated in place
     * @return success, or a classified failure describing why the flag was not written
     */
    @NotNull
    EmakiResult<Unit> markDisabled(@Nullable ItemStack itemStack);

    /**
     * Removes the repair-disabled marker from a stack through the real repair service, mutating the
     * given stack in place.
     *
     * <p>Shares {@link #markDisabled}'s contract exactly: {@code INVALID_INPUT} for a {@code null} or
     * air stack, {@code UNAVAILABLE} without the repair service, and {@code INTERNAL_ERROR} when the
     * removal does not survive a read-back or a runtime exception escapes the commit. Clearing a stack
     * that was never disabled is a normal success, not a failure.
     *
     * @param itemStack the stack to clear, mutated in place
     * @return success, or a classified failure describing why the flag was not cleared
     */
    @NotNull
    EmakiResult<Unit> clearDisabled(@Nullable ItemStack itemStack);

    /**
     * Quotes the configured economy repair costs.
     *
     * <p>An unaffordable quote is still a successful quote whose {@link RepairQuoteView#affordable()} is
     * {@code false}. A missing economy provider is likewise reported through the returned view rather
     * than as an API failure, so callers read affordability from the view and reserve failure handling
     * for the cases below.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread; calling elsewhere returns
     * {@code WRONG_THREAD} instead of quoting.
     *
     * <p>Failure branches: {@code INVALID_INPUT} for a {@code null} player or a {@code null}/air stack,
     * {@code TARGET_OFFLINE} for an offline player, {@code UNAVAILABLE} when EmakiItem's repair service
     * or identity services are not installed, {@code REJECTED} when the stack is not an EmakiItem-managed
     * item or the item's repair or economy repair is switched off in config, {@code NOT_FOUND} when the
     * resolved item id has no definition, and {@code INTERNAL_ERROR} when quoting throws.
     *
     * @param player     the player whose balances are quoted against
     * @param itemStack  the managed stack to quote a repair for
     * @return the quote, or a classified failure describing why no quote was produced
     */
    @NotNull
    EmakiResult<RepairQuoteView> quote(@Nullable Player player, @Nullable ItemStack itemStack);

    /**
     * Executes the configured economy repair, including event, charge, compensation, durability commit,
     * and post-repair actions from the runtime service.
     *
     * <p>The stack is repaired in place on success and the returned {@link RepairOutcome} reports the
     * restored amount together with the post-repair damage state read back from the stack.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread. This method is synchronous and
     * returns {@code WRONG_THREAD} elsewhere rather than scheduling the work.
     *
     * <p>Failure branches match {@link #quote}'s validation, plus: {@code CANCELLED} when a listener
     * cancels the runtime repair event, {@code REJECTED} when the runtime refuses the repair &mdash;
     * unaffordable currencies, a listener zeroing the restore amount, a failed charge, or an item that
     * turned out to need no repair &mdash; and {@code INTERNAL_ERROR} when the repair throws or the
     * runtime refuses without a reason key. On a rejection after money was already taken the runtime
     * rolls the debits back, so a failed call is not expected to leave the player charged.
     *
     * @param player     the player paying for and receiving the repair
     * @param itemStack  the managed stack to repair, mutated in place on success
     * @return the committed outcome, or a classified failure describing why nothing was repaired
     */
    @NotNull
    EmakiResult<RepairOutcome> repair(@Nullable Player player, @Nullable ItemStack itemStack);
}
