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
 * State-changing skill operations.
 *
 * <p>Reached through {@code EmakiSkillsApi.operations()}. When EmakiSkills is absent every method returns
 * a {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#UNAVAILABLE} result instead of {@code null}
 * or an exception, so callers must classify on {@code FailureKind} rather than catching
 * {@code NullPointerException}.
 *
 * <p><strong>Thread:</strong> every method except {@link #cast} and {@link #castByTrigger} is synchronous
 * and must run on the player's owner thread; calling from another thread returns
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD} rather than silently
 * rescheduling. All ids are matched after normalization (trimmed, lower-cased with {@code Locale.ROOT},
 * spaces folded to {@code _}), so casing and surrounding whitespace do not matter.
 *
 * <h2>Business outcome is not API failure</h2>
 * A refused business decision and a broken call are different things. {@link #upgrade} reports a lost
 * success-rate roll as a <em>successful</em> result whose {@link SkillUpgradeOutcome#successfulRoll()} is
 * {@code false}; only a call that could not be processed at all becomes a failure result.
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
     * <p>Failure kinds a caller can act on: {@code INVALID_INPUT} for a {@code null} player or blank skill
     * id, {@code TARGET_OFFLINE} when the player is offline or has no loaded profile, {@code UNAVAILABLE}
     * while the runtime is not ready, {@code NOT_FOUND} when the skill is not equipped in a slot that also
     * has a trigger bound, {@code CANCELLED} when a listener cancels
     * {@link emaki.jiuwu.craft.skills.api.event.SkillPreCastEvent}, and {@code REJECTED} for the ordinary
     * gates — cast mode disabled, cooldown or forced delay active, unmet conditions, insufficient
     * resources, or another attempt already in flight for the same skill.
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
     * <p>Threading, the completion-thread caveat and the failure classification are the same as
     * {@link #cast(Player, String)}, with one difference: a blank trigger id fails as {@code INVALID_INPUT},
     * and a trigger with no non-empty slot binding fails as {@code NOT_FOUND} rather than being reported
     * against a skill id.
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
     * @param player  the player to teach; {@code null} yields {@code INVALID_INPUT}, offline yields
     *                {@code TARGET_OFFLINE}
     * @param skillId the skill to learn; blank yields {@code INVALID_INPUT} and an unknown id yields
     *                {@code NOT_FOUND}
     * @return success, or {@code REJECTED} when nothing was added because the skill was already learned
     *         manually or is currently disabled
     */
    @NotNull EmakiResult<Unit> learn(@Nullable Player player, @Nullable String skillId);

    /**
     * Removes a skill from the player's manually learned set, leaving grants from equipment or external
     * providers in place.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player  the player to modify
     * @param skillId the skill to forget; blank yields {@code INVALID_INPUT} and an unknown id yields
     *                {@code NOT_FOUND}
     * @return success, or {@code REJECTED} when the skill was not manually learned in the first place
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
     * @return success, or {@code REJECTED} when the runtime refuses the change — slot not unlocked for this
     *         player, skill not in the player's unlocked pool, skill not active/slot-visible, the same skill
     *         already equipped elsewhere, or a listener cancelling the slot-change event. These causes are
     *         distinguished by {@code reasonKey}, not by kind.
     */
    @NotNull EmakiResult<Unit> equip(@Nullable Player player, int slotIndex, @Nullable String skillId);

    /**
     * Empties one of the player's skill slots.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player    the player whose slots change
     * @param slotIndex the slot to clear; negative yields {@code INVALID_INPUT}
     * @return success, or {@code REJECTED} when the slot does not exist for this player or a listener
     *         cancels the slot-change event
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
     * @return success, or {@code REJECTED} when the slot is empty or missing, the trigger id is not a
     *         recognised trigger, the trigger conflicts with another of the player's bindings, or a listener
     *         cancels the slot-change event
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
     * @return the committed upgrade outcome, {@code CANCELLED} when a listener vetoes the upgrade,
     *         {@code REJECTED} when the runtime refuses it (upgrades disabled, already at maximum level,
     *         unpaid costs — see {@code reasonKey}), or {@code INTERNAL_ERROR} when the upgrade service
     *         returns no result at all
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
     * @return the level actually stored after clamping into {@code [1, maxLevel]}, or a classified failure.
     *         A skill without upgrades enabled has a maximum of {@code 1}, so it always stores {@code 1}.
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
     * @return the level actually stored after clamping into {@code [1, maxLevel]}, or a classified failure
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
