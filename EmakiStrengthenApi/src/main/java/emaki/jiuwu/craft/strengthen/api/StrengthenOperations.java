package emaki.jiuwu.craft.strengthen.api;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.strengthen.api.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.api.model.AttemptResult;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementAttemptContext;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementAttemptOutcome;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementOperationView;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementPityStateView;
import emaki.jiuwu.craft.strengthen.api.model.ItemMasteryView;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenTransferOutcome;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;

/**
 * State-changing EmakiStrengthen operations.
 *
 * <p><strong>Thread:</strong> player-scoped methods must be called on that player's entity-owner
 * thread. Item-only methods must be called on the owner thread of the inventory, entity, or region
 * holding the stack. Detached item copies may be processed on the caller's current thread.
 */
@ApiStatus.NonExtendable
public interface StrengthenOperations {

    /**
     * Performs one strengthen attempt using cloned input data.
     *
     * <p>A committed failed roll is a successful API operation carrying an {@link AttemptResult}
     * whose {@link AttemptResult#success()} is {@code false}. Early validation, cancellation, charge,
     * or rebuild failures are returned as {@link EmakiResult.Failure} values. When a cost was taken but
     * compensation is still outstanding the call returns {@code Partial}, which is the signal that the
     * player's balance may not be settled yet.
     *
     * <p>The attempt does not write the result back into any inventory. The rebuilt stack is carried on
     * the returned {@link AttemptResult}, and applying it is the caller's job.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread. Called from anywhere else the method
     * returns {@code WRONG_THREAD} rather than rescheduling itself.
     *
     * @param player  the online player performing the attempt; {@code null} yields {@code INVALID_INPUT}
     *                and an offline player yields {@code TARGET_OFFLINE}
     * @param context the attempt inputs; {@code null}, or a context whose target item is {@code null},
     *                yields {@code INVALID_INPUT}. A blank operation id is replaced with a generated
     *                one; reusing an id lets the runtime return the previous result instead of rolling
     *                twice
     * @return the committed attempt outcome, a {@code Partial} carrying an unsettled compensation, or a
     *         classified failure
     */
    @NotNull
    EmakiResult<AttemptResult> attempt(@Nullable Player player, @Nullable AttemptContext context);

    /**
     * Performs one provider-based generic enhancement attempt using an enhancement recipe.
     *
     * <p>Strengthen owns material matching/consumption, currency charging, chance, pity, idempotency,
     * compensation and recipe actions. The registered target provider owns target recognition and the
     * final level/stage write-back. The caller owns applying the returned cloned stacks to its GUI or
     * inventory state.
     *
     * <p>A committed failed roll is returned as a successful API operation whose outcome has
     * {@code success=false}. Rejected validation is returned as {@link EmakiResult.Failure}; unsettled
     * compensation is returned as {@code Partial}.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread.
     */
    @NotNull
    EmakiResult<EnhancementAttemptOutcome> attemptEnhancement(@Nullable Player player,
            @Nullable EnhancementAttemptContext context);

    /**
     * Transfers strengthened stars from a source item to a target item.
     *
     * <p>The transferred star count is {@code floor(sourceStar * decayRate)}, then capped at the target
     * recipe's maximum star. A listener of {@code StrengthenTransferEvent} may adjust that count, and a
     * count that ends up at zero or below is rejected rather than treated as a no-op success.
     *
     * <p>Only the rebuilt target is returned, on the outcome. The source item is not consumed or cleared
     * by this call; deciding what happens to it is the caller's job.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread. Called from anywhere else the method
     * returns {@code WRONG_THREAD}.
     *
     * @param player    the online player performing the transfer; {@code null} yields
     *                  {@code INVALID_INPUT} and an offline player yields {@code TARGET_OFFLINE}
     * @param source    the item donating stars; {@code null} or air yields {@code INVALID_INPUT}, and an
     *                  unstrengthened source is rejected
     * @param target    the item receiving stars; {@code null} or air yields {@code INVALID_INPUT}, and a
     *                  target with no usable strengthen recipe is rejected
     * @param decayRate the surviving fraction of the source stars; clamped to {@code [0, 1]}, so
     *                  {@code 1} transfers everything the target's cap allows. A non-finite value yields
     *                  {@code INVALID_INPUT}
     * @return the rebuilt target and the star count actually applied, or a classified failure
     */
    @NotNull
    EmakiResult<StrengthenTransferOutcome> transfer(@Nullable Player player,
            @Nullable ItemStack source,
            @Nullable ItemStack target,
            double decayRate);

    /**
     * Rebuilds the strengthen layer from stored state and returns the new stack.
     *
     * <p>An item with no strengthen layer returns a partial result rather than being
     * indistinguishable from an unavailable API. Unlike {@link #refreshItem}, an item that carries a
     * layer whose recipe is no longer loaded is reported as {@code NOT_FOUND} instead of being left
     * alone, which makes this the stricter of the two for auditing stale items after a reload.
     *
     * <p>The supplied stack is not modified in place; nothing is written back to any inventory.
     *
     * <p><strong>Thread:</strong> the owner thread of whatever holds the stack. A detached copy may be
     * processed on the caller's current thread.
     *
     * @param itemStack the stack to rebuild; {@code null} or air yields {@code INVALID_INPUT}
     * @return the rebuilt stack, a {@code Partial} copy when the item has no strengthen layer, or a
     *         classified failure
     */
    @NotNull
    EmakiResult<ItemStack> rebuild(@Nullable ItemStack itemStack);

    /**
     * Opens the strengthen GUI for one player.
     *
     * <p>A GUI that declines to open, for example because the view could not be built, is reported as
     * {@code REJECTED} rather than as a silent success.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread. Called from anywhere else the method
     * returns {@code WRONG_THREAD}.
     *
     * @param player the online player to show the GUI to; {@code null} yields {@code INVALID_INPUT} and
     *               an offline player yields {@code TARGET_OFFLINE}
     * @return success, or a failure describing why the GUI did not open
     */
    @NotNull
    EmakiResult<Unit> openGui(@Nullable Player player);

    /**
     * Rebuilds one item's strengthen layer against the currently loaded definitions.
     *
     * <p>Use this after a configuration reload so an existing item picks up new stats. An item the
     * runtime left untouched, whether because it carries no strengthen layer or because its recipe is
     * missing, comes back as a {@code Partial} copy, which separates "nothing to do" from "refreshed".
     * {@link #rebuild} is stricter about the missing-recipe case.
     *
     * <p>The supplied stack is not modified in place; nothing is written back to any inventory.
     *
     * <p><strong>Thread:</strong> the owner thread of whatever holds the stack. A detached copy may be
     * processed on the caller's current thread.
     *
     * @param itemStack the stack to refresh; {@code null} or air yields {@code INVALID_INPUT}
     * @return the refreshed stack, a {@code Partial} copy when nothing needed rebuilding, or a
     *         classified failure
     */
    @NotNull
    EmakiResult<ItemStack> refreshItem(@Nullable ItemStack itemStack);

    /**
     * Refreshes strengthened items in a player's inventory, armour, and off-hand, writing the rebuilt
     * stacks back into those slots.
     *
     * <p>Coverage is storage contents, armour slots, and the off-hand. The cursor stack and any open
     * container the player is viewing are not included, so an item being dragged during the call keeps
     * its old layer.
     *
     * <p>A count of {@code 0} is a legitimate success meaning nothing needed rebuilding; it is not an
     * error signal.
     *
     * <p><strong>Thread:</strong> the player's entity-owner thread. Called from anywhere else the method
     * returns {@code WRONG_THREAD}.
     *
     * @param player the online player whose inventory is refreshed; {@code null} yields
     *               {@code INVALID_INPUT} and an offline player yields {@code TARGET_OFFLINE}
     * @return the number of slots whose item was rebuilt, or a failure describing why the refresh did
     *         not run
     */
    @NotNull
    EmakiResult<Integer> refreshPlayer(@Nullable Player player);

    /**
     * Registers an enhancement target provider, making its target type usable by enhancement recipes
     * whose {@code target.provider} matches the provider id.
     *
     * <p>Registering an id that is already present replaces the previous provider rather than failing,
     * which is what a reloading caller wants. Providers are not restored automatically when
     * EmakiStrengthen reloads, so a caller that caches nothing should re-register from its own
     * readiness listener.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param provider the provider to register; {@code null}, or one whose {@link
     *                 EnhancementTargetProvider#id()} is blank, yields {@code INVALID_INPUT}
     * @return {@link Unit} on success, or a classified failure
     */
    @NotNull
    EmakiResult<Unit> registerEnhancementTarget(@Nullable EnhancementTargetProvider provider);

    /**
     * Removes a previously registered enhancement target provider.
     *
     * <p>Removing an id that is not registered is reported as {@code NOT_FOUND} rather than a silent
     * success, so a caller can tell a stale id from a real removal.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param providerId the provider id to remove; {@code null} or blank yields {@code INVALID_INPUT}
     * @return {@link Unit} on success, or a classified failure
     */
    @NotNull
    EmakiResult<Unit> unregisterEnhancementTarget(@Nullable String providerId);

    /**
     * Looks up one enhancement operation in the transaction journal.
     *
     * <p>This is the query path for an operation id handed to a player alongside a
     * {@code strengthen.error.compensation_pending} message. An id that is absent is reported as
     * {@code NOT_FOUND}, which for a settled operation legitimately means "already pruned" rather
     * than "never existed": the runtime keeps unsettled entries and drops completed ones.
     *
     * <p>The lookup is read-only. It neither retries compensation nor mutates the journal.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param operationId the operation id to look up; {@code null} or blank yields
     *                    {@code INVALID_INPUT}
     * @return the journal view, or a classified failure
     */
    @NotNull
    default EmakiResult<EnhancementOperationView> enhancementOperation(@Nullable String operationId) {
        return EmakiResult.unavailable();
    }

    /**
     * Lists the enhancement operations still held in the transaction journal.
     *
     * <p>Retained entries are the in-flight ones and those whose compensation has not settled, so an
     * empty list is the healthy steady state rather than a sign the journal is unavailable. Use
     * {@link EnhancementOperationView#compensationPending()} to separate entries that need operator
     * attention from ones merely still running.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @return the retained operations, or a classified failure
     */
    @NotNull
    default EmakiResult<List<EnhancementOperationView>> enhancementOperations() {
        return EmakiResult.unavailable();
    }

    /**
     * Lists stored pity counter records, optionally narrowed to one group.
     *
     * <p>An empty list is a legitimate result meaning no counter has been written yet, not a sign the
     * store is unavailable. Records whose group carries an isolation suffix are included when
     * {@code group} matches their base group, so an operator does not have to know the suffix.
     *
     * <p>The listing is read-only. It neither advances nor resets any counter.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param group the counter group to filter by; {@code null} or blank lists every record
     * @return the matching records, or a classified failure when the pity store is unavailable
     */
    @NotNull
    default EmakiResult<List<EnhancementPityStateView>> pityStates(@Nullable String group) {
        return EmakiResult.unavailable();
    }

    /**
     * Overwrites one pity counter.
     *
     * <p>This writes the counter the runtime will read on the next attempt, so it can both arm and
     * disarm a pending guarantee. The write only updates memory and marks the store dirty; it is
     * flushed by the normal lifecycle or retry path rather than synchronously.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param scope    the counter scope, {@code player} or {@code item}; an unknown value yields
     *                 {@code INVALID_INPUT}
     * @param group    the counter group exactly as stored, including any isolation suffix; blank
     *                 yields {@code INVALID_INPUT}
     * @param ownerKey the owner identity within the scope; blank yields {@code INVALID_INPUT}
     * @param counter  the new counter value; negative values are clamped to {@code 0}
     * @return {@link Unit} on success, or a classified failure
     */
    @NotNull
    default EmakiResult<Unit> setPityCounter(@Nullable String scope,
            @Nullable String group,
            @Nullable String ownerKey,
            int counter) {
        return EmakiResult.unavailable();
    }

    /**
     * Removes every pity record belonging to one group.
     *
     * <p>Records whose group carries an isolation suffix are removed together with the base group, so
     * clearing {@code weapon_pity} also clears {@code weapon_pity#level=7}. A count of {@code 0} is a
     * legitimate success meaning the group held no records.
     *
     * <p><strong>Thread:</strong> any thread.
     *
     * @param group the base counter group; {@code null} or blank yields {@code INVALID_INPUT}
     * @return the number of records removed, or a classified failure
     */
    @NotNull
    default EmakiResult<Integer> clearPityGroup(@Nullable String group) {
        return EmakiResult.unavailable();
    }

    /**
     * Overwrites one item's accumulated mastery experience and returns the resulting snapshot.
     *
     * <p>This replaces the total rather than adding to it, so it can both grant and revoke progress.
     * Level and current-level experience are derived from the total, and milestones are rebuilt to match,
     * which means a lowered total also drops the milestones it no longer justifies.
     *
     * <p>The supplied stack is modified in place. Nothing is written back to any inventory, so a caller
     * holding a detached copy must apply it themselves.
     *
     * <p><strong>Thread:</strong> the owner thread of whatever holds the stack. A detached copy may be
     * processed on the caller's current thread.
     *
     * @param itemStack the stack whose mastery is rewritten; {@code null} or air yields
     *                  {@code INVALID_INPUT}
     * @param totalExp  the new accumulated experience total; negative or non-finite yields
     *                  {@code INVALID_INPUT}
     * @return the snapshot after the write, or a classified failure
     */
    @NotNull
    default EmakiResult<ItemMasteryView> setMastery(@Nullable ItemStack itemStack, double totalExp) {
        return EmakiResult.unavailable();
    }

    /**
     * Removes one item's entire mastery layer.
     *
     * <p>Clearing an item that carries no mastery is reported as {@code NOT_FOUND} rather than a silent
     * success, so a caller can tell a stale reference from a real removal. A cleared item is
     * indistinguishable from one that never accumulated mastery: the instance id is dropped too, so
     * later progress starts under a fresh identity.
     *
     * <p>The supplied stack is modified in place.
     *
     * <p><strong>Thread:</strong> the owner thread of whatever holds the stack.
     *
     * @param itemStack the stack to clear; {@code null} or air yields {@code INVALID_INPUT}
     * @return {@link Unit} on success, or a classified failure
     */
    @NotNull
    default EmakiResult<Unit> clearMastery(@Nullable ItemStack itemStack) {
        return EmakiResult.unavailable();
    }
}
