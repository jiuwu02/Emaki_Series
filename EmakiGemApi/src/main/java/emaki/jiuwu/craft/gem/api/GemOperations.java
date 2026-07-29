package emaki.jiuwu.craft.gem.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.gem.api.model.GemExtractOutcome;
import emaki.jiuwu.craft.gem.api.model.GemInlayOutcome;
import emaki.jiuwu.craft.gem.api.model.GemSocketOpenOutcome;

/**
 * State-changing gem operations.
 *
 * <p>Reached through {@code EmakiGemApi.operations()}.
 *
 * <h2>Threading — mandatory</h2>
 * Every method must be called on the thread that owns {@code actor}. On Folia that is the player's
 * region thread. Calls from any other thread report
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD} and change nothing.
 *
 * <p>This is stricter than a convention. EmakiGem guards its own event fire points with an ownership
 * check and <em>silently skips the event</em> when the check fails, so an off-thread write would
 * complete without ever giving listeners a chance to cancel it. Rejecting the call is the only way to
 * keep the event contract honest.
 *
 * <h2>Transactions are completed for you</h2>
 * EmakiGem's internal inlay and extract calls hand back a pending commit action; skipping it leaves the
 * operation journal entry open, which the next server start treats as "charged but never finished" and
 * compensates by refunding the player. This API always performs that commit before returning, so the
 * stacks in the returned outcome are final and the journal entry is closed. The commit action is
 * deliberately not exposed.
 *
 * <h2>Writing results back</h2>
 * These methods return new stacks rather than mutating the ones you pass in. You are responsible for
 * placing {@code updatedEquipment} (and any returned gem) back into the inventory, container, or entity
 * it came from.
 */
@ApiStatus.NonExtendable
public interface GemOperations {

    /**
     * Inlays a gem into one socket slot.
     *
     * <p>Fires {@code GemInlayEvent} before charging costs; a listener may cancel it. Cancellation and
     * an unmet precondition both surface as
     * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#REJECTED} because EmakiGem reports them
     * with the same reason key — listen to {@code GemInlayEvent} yourself if you need to tell them
     * apart. On success {@code GemInlayCompletedEvent} is fired after the transaction is committed.
     *
     * @param actor      the player performing the inlay, whose costs and conditions are evaluated
     * @param equipment  the equipment to receive the gem
     * @param gemItem    the gem item to consume
     * @param slotIndex  the target slot index
     * @param bypassCost whether to skip currency and material costs
     * @return the committed outcome, or a failure describing why the inlay did not happen
     */
    @NotNull
    EmakiResult<GemInlayOutcome> inlay(@Nullable Player actor,
                                       @Nullable ItemStack equipment,
                                       @Nullable ItemStack gemItem,
                                       int slotIndex,
                                       boolean bypassCost);

    /**
     * Extracts the gem from one socket slot.
     *
     * <p>Fires {@code GemExtractEvent} before charging costs; cancellation and an unmet precondition both
     * surface as {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#REJECTED}. On success
     * {@code GemExtractCompletedEvent} is fired after the transaction is committed.
     *
     * @param actor      the player performing the extraction
     * @param equipment  the equipment holding the gem
     * @param slotIndex  the slot to empty
     * @param bypassCost whether to skip currency and material costs
     * @return the committed outcome, or a failure describing why the extraction did not happen
     */
    @NotNull
    EmakiResult<GemExtractOutcome> extract(@Nullable Player actor,
                                           @Nullable ItemStack equipment,
                                           int slotIndex,
                                           boolean bypassCost);

    /**
     * Opens a socket slot using an opener item.
     *
     * <p>Fires {@code GemSocketOpenEvent}; cancellation and an unmet requirement both surface as
     * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#REJECTED}.
     *
     * @param actor             the player opening the socket
     * @param equipment         the equipment to modify
     * @param openerItem        the opener item to consume
     * @param slotIndex         the slot to open
     * @param bypassRequirement whether to skip the opener's own requirement checks
     * @return the outcome, or a failure describing why the socket was not opened
     */
    @NotNull
    EmakiResult<GemSocketOpenOutcome> openSocket(@Nullable Player actor,
                                                 @Nullable ItemStack equipment,
                                                 @Nullable ItemStack openerItem,
                                                 int slotIndex,
                                                 boolean bypassRequirement);

    /**
     * Builds a fresh gem item.
     *
     * <p><strong>Thread:</strong> any thread; this only assembles an item and touches no player state.
     *
     * @param gemId  the gem definition id
     * @param level  the gem level; values below one are treated as one
     * @param amount the stack size; values below one are treated as one
     * @return the gem item, or a failure when the definition is unknown or its item source is
     *         unresolvable
     */
    @NotNull
    EmakiResult<ItemStack> createGemItem(@Nullable String gemId, int level, int amount);

    /**
     * Removes the entire gem layer from a piece of equipment, discarding every inlaid gem without
     * returning any of them.
     *
     * <p><strong>Thread:</strong> the owner thread of whatever holds the stack.
     *
     * @param equipment the equipment to clear
     * @return the cleared equipment, or a failure when the stack carries no gem layer
     */
    @NotNull
    EmakiResult<ItemStack> clearGems(@Nullable ItemStack equipment);

    /**
     * Opens the gem inlay GUI.
     *
     * @param player the player to show the GUI to
     * @return success, or a failure describing why the GUI did not open
     */
    @NotNull
    EmakiResult<Unit> openGui(@Nullable Player player);

    /**
     * Opens the socket-opening GUI.
     *
     * @param player the player to show the GUI to
     * @param target the equipment to preselect, or {@code null} for none
     * @return success, or a failure describing why the GUI did not open
     */
    @NotNull
    EmakiResult<Unit> openSocketGui(@Nullable Player player, @Nullable ItemStack target);
}
