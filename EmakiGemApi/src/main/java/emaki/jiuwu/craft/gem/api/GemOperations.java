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
import emaki.jiuwu.craft.gem.api.model.GemRerollOutcome;

/**
 * State-changing gem operations.
 *
 * <p>Reached through {@link EmakiGemApi#operations()}.
 *
 * <h2>Threading</h2>
 * Player-scoped methods must run on the acting player's owner thread. On Folia this is the entity's
 * region thread. A wrong-thread call returns
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD} and changes nothing.
 * Methods that only transform a detached item must run on the owner thread of the inventory, entity, or
 * container that owns that item.
 *
 * <h2>Transaction completion</h2>
 * Inlay and extraction never expose the runtime's pending commit action. The bridge commits internally
 * before returning. The returned equipment is therefore the state that callers must write back. The
 * matching completed event is emitted later only after success actions finish and the persistent
 * operation journal reaches its terminal {@code COMPLETED} phase.
 */
@ApiStatus.NonExtendable
public interface GemOperations {

    /**
     * Inlays one loose gem into a socket.
     *
     * <p>The input equipment is not mutated. The supplied gem stack is not decremented; callers must
     * consume it when {@link GemInlayOutcome#inputConsumed()} is {@code true} and write
     * {@link GemInlayOutcome#updatedEquipment()} back to its owner.
     *
     * @param actor     the acting player
     * @param equipment the target equipment
     * @param gemItem   the loose gem item
     * @param slotIndex the target socket index
     * @return the committed outcome
     */
    @NotNull
    EmakiResult<GemInlayOutcome> inlay(@Nullable Player actor,
                                       @Nullable ItemStack equipment,
                                       @Nullable ItemStack gemItem,
                                       int slotIndex);

    /**
     * Extracts the gem in one socket.
     *
     * @param actor      the acting player
     * @param equipment  the target equipment
     * @param slotIndex  the socket index
     * @param bypassCost whether configured extraction costs are skipped
     * @return the committed equipment and optional returned gem
     */
    @NotNull
    EmakiResult<GemExtractOutcome> extract(@Nullable Player actor,
                                           @Nullable ItemStack equipment,
                                           int slotIndex,
                                           boolean bypassCost);

    /**
     * Opens the first compatible closed socket using a configured opener item.
     *
     * <p>The returned value is the updated equipment. On success the supplied opener stack is adjusted
     * in place to reflect consumption, because the delivery specification intentionally exposes no
     * second-stack outcome type.
     *
     * @param actor      the acting player
     * @param equipment  the target equipment
     * @param openerItem the configured opener item
     * @return the updated equipment
     */
    @NotNull
    EmakiResult<ItemStack> openSocket(@Nullable Player actor,
                                      @Nullable ItemStack equipment,
                                      @Nullable ItemStack openerItem);

    /**
     * Creates a loose gem item.
     *
     * <p><strong>Thread:</strong> any thread; the method assembles a detached item only.
     *
     * @param gemId  the gem definition id
     * @param level  the gem level; values below one are treated as one
     * @param amount the stack amount; values below one are treated as one
     * @return the created item
     */
    @NotNull
    EmakiResult<ItemStack> createGemItem(@Nullable String gemId, int level, int amount);

    /**
     * Removes the entire gem layer from an equipment item.
     *
     * @param equipment the equipment to clear
     * @return the rebuilt item
     */
    @NotNull
    EmakiResult<ItemStack> clearGems(@Nullable ItemStack equipment);

    /**
     * Opens the inlay GUI.
     *
     * @param player the player to show it to
     * @return success when the GUI opened
     */
    @NotNull
    EmakiResult<Unit> openGui(@Nullable Player player);

    /**
     * Opens the socket-opening GUI.
     *
     * @param player the player to show it to
     * @param target optional equipment to preselect
     * @return success when the GUI opened
     */
    @NotNull
    EmakiResult<Unit> openSocketGui(@Nullable Player player, @Nullable ItemStack target);
}
