package emaki.jiuwu.craft.skills.api;

import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.skills.api.model.SkillCastOutcome;
import emaki.jiuwu.craft.skills.api.model.SkillUpgradeOutcome;

/**
 * Skill mutations and asynchronous cast entry points.
 *
 * <p>Synchronous methods require the player's owner thread and do not reschedule. Cast methods accept any
 * caller thread, dispatch player work automatically, and return futures whose completion thread is not
 * guaranteed. Missing EmakiSkills produces non-null unavailable results. A business outcome such as a
 * failed upgrade roll is still a successful API call.
 */
@ApiStatus.NonExtendable
public interface SkillOperations {

    /**
     * Casts the skill the player currently has equipped, resolving the trigger from that equipped binding.
     *
     * <p><strong>Thread:</strong> callable from any thread. Argument and availability guards are evaluated
     * on the calling thread; binding resolution, the cast pipeline's Bukkit phases and the pre/post cast
     * events are dispatched onto the player's owner thread, including on Folia. The future is genuine —
     * scripts and integrations may complete it much later — and <strong>the completion thread is not
     * guaranteed</strong>: guard failures complete inline on the calling thread and other paths complete on
     * whichever thread finished the pipeline. A continuation that touches the player, inventory, world or a
     * GUI must hop back to the owner thread itself.
     *
     * <p>Validation, availability, cancellation and ordinary cast gates use the shared {@link EmakiResult}
     * failure contract; the notable business distinction is that a rejected cast does not imply an API fault.
     *
     * @param player  the caster; must be online, and cast mode must be enabled for the attempt to pass the
     *                binding gate
     * @param skillId the skill to cast, matched case-insensitively after id normalization; must resolve to
     *                an enabled skill that is equipped and trigger-bound
     * @return a future carrying the cast outcome, or a classified failure; never {@code null}
     */
    @NotNull CompletableFuture<EmakiResult<SkillCastOutcome>> cast(
            @Nullable Player player, @Nullable String skillId);

    /**
     * Casts whichever skill is bound to a trigger id, skipping the skill-to-trigger lookup that
     * {@link #cast(Player, String)} performs.
     *
     * <p>Threading and future-completion rules match {@link #cast(Player, String)}. A blank trigger id is
     * invalid, and a trigger without a non-empty slot binding is a not-found result.
     *
     * @param player    the caster; must be online with cast mode enabled
     * @param triggerId the trigger whose bound skill is cast, matched after id normalization; blank is
     *                  rejected as invalid input
     * @return a future carrying the cast outcome, or a classified failure; never {@code null}
     */
    @NotNull CompletableFuture<EmakiResult<SkillCastOutcome>> castByTrigger(
            @Nullable Player player, @Nullable String triggerId);

    /**
     * Adds a skill to the player's manually learned set, which is only one of the sources that can unlock a
     * skill; equipment and third-party providers are unaffected by this call.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player  the player to teach
     * @param skillId the skill to learn
     * @return success when the manual grant changes; already-learned or disabled skills are reported through
     *         the shared failure contract
     */
    @NotNull EmakiResult<Unit> learn(@Nullable Player player, @Nullable String skillId);

    /**
     * Removes a skill from the player's manually learned set, leaving grants from equipment or external
     * providers in place.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player  the player to modify
     * @param skillId the skill to forget
     * @return success when the manual grant is removed; an absent manual grant is reported through the shared
     *         failure contract
     */
    @NotNull EmakiResult<Unit> forget(@Nullable Player player, @Nullable String skillId);

    /**
     * Clears the player's whole manually learned set in one pass.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player the player to clear
     * @return the number of manual entries removed, which is {@code 0} for an already empty set, or a
     *         classified failure
     */
    @NotNull EmakiResult<Integer> forgetAll(@Nullable Player player);

    /**
     * Puts a skill into one of the player's skill slots, keeping any trigger already bound to that slot.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player    the player whose slots change
     * @param slotIndex the target slot; negative yields {@code INVALID_INPUT}
     * @param skillId   the skill to equip; blank yields {@code INVALID_INPUT} and an unknown id yields
     *                  {@code NOT_FOUND}
     * @return success when the slot changes; unlock, visibility, duplicate-skill and listener-veto cases use
     *         the shared failure contract, with their detail in {@code reasonKey}
     */
    @NotNull EmakiResult<Unit> equip(@Nullable Player player, int slotIndex, @Nullable String skillId);

    /**
     * Empties one of the player's skill slots.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player    the player whose slots change
     * @param slotIndex the slot to clear; negative yields {@code INVALID_INPUT}
     * @return success when the slot is cleared; invalid slot and listener-veto cases use the shared failure
     *         contract
     */
    @NotNull EmakiResult<Unit> unequip(@Nullable Player player, int slotIndex);

    /**
     * Binds a trigger to an occupied skill slot so the equipped skill becomes castable.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player    the player whose bindings change
     * @param slotIndex the slot to bind; negative yields {@code INVALID_INPUT}
     * @param triggerId the trigger to bind; blank yields {@code INVALID_INPUT}
     * @return success when the binding changes; empty-slot, unknown-trigger, conflict and listener-veto cases
     *         use the shared failure contract
     */
    @NotNull EmakiResult<Unit> bindTrigger(@Nullable Player player, int slotIndex, @Nullable String triggerId);

    /**
     * Runs one upgrade process, charging its costs and rolling its success rate.
     *
     * <p>A failed success-rate roll is still a successful process: the result is a success carrying
     * {@link SkillUpgradeOutcome#successfulRoll()} {@code == false}, possibly with
     * {@link SkillUpgradeOutcome#downgraded()} set. Do not treat that as an API failure.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player  the player being upgraded
     * @param skillId the skill to upgrade; blank yields {@code INVALID_INPUT} and an unknown id yields
     *                {@code NOT_FOUND}
     * @return the committed upgrade outcome through the shared result contract. A failed success-rate roll is
     *         still a successful business outcome; listener veto and unpaid/disabled/maxed cases are failures.
     */
    @NotNull EmakiResult<SkillUpgradeOutcome> upgrade(@Nullable Player player, @Nullable String skillId);

    /**
     * Sets a skill's level directly, bypassing costs and success rolls.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player  the player to modify
     * @param skillId the skill to modify; blank yields {@code INVALID_INPUT} and an unknown id yields
     *                {@code NOT_FOUND}
     * @param level   the requested level; values below {@code 1} yield {@code INVALID_INPUT}, and values
     *                above the skill's maximum are clamped rather than rejected
     * @return the level actually stored after clamping into {@code [1, maxLevel]}; failures use the shared
     *         result contract. A skill without upgrades enabled has a maximum of {@code 1}.
     */
    @NotNull EmakiResult<Integer> setLevel(@Nullable Player player, @Nullable String skillId, int level);

    /**
     * Shifts a skill's level by a signed delta, bypassing costs and success rolls.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player  the player to modify
     * @param skillId the skill to modify; blank yields {@code INVALID_INPUT} and an unknown id yields
     *                {@code NOT_FOUND}
     * @param delta   the signed change; {@code 0} is rejected as {@code INVALID_INPUT} because it would be a
     *               no-op write
     * @return the level actually stored after clamping into {@code [1, maxLevel]}, with failures represented by
     *         the shared result contract
     */
    @NotNull EmakiResult<Integer> addLevel(@Nullable Player player, @Nullable String skillId, int delta);

    /**
     * Turns the player's cast mode on or off. Cast mode gates the trigger-bound cast paths, so
     * {@link #cast} and {@link #castByTrigger} are rejected while it is off.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player  the player to modify
     * @param enabled {@code true} to enable cast mode, {@code false} to disable it
     * @return success once the state is written — setting the value it already has is not an error — or a
     *         classified failure
     */
    @NotNull EmakiResult<Unit> setCastMode(@Nullable Player player, boolean enabled);
}
