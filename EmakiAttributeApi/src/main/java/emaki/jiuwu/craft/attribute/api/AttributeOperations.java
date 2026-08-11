package emaki.jiuwu.craft.attribute.api;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.attribute.api.model.DamageResult;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;

/**
 * State-changing resource, damage and synchronization operations.
 *
 * <p>Reached through {@code EmakiAttributeApi.operations()}. When EmakiAttribute is absent every method
 * returns a {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#UNAVAILABLE} result rather than
 * {@code null} or an exception, so callers must classify on {@code FailureKind} instead of catching
 * {@code NullPointerException}.
 *
 * <p><strong>Thread:</strong> these are synchronous methods that touch live entity state and must run on the
 * relevant owner thread, returning {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#WRONG_THREAD}
 * elsewhere rather than silently rescheduling. {@link #scheduleEquipmentSync(Player)} is the exception: it
 * only enqueues work and accepts calls from any thread.
 *
 * <p>Resource and damage-type ids are normalized before matching (trimmed, lower-cased with
 * {@code Locale.ROOT}, spaces folded to {@code _}).
 */
@ApiStatus.NonExtendable
public interface AttributeOperations {

    /**
     * Consumes a resource after firing
     * {@link emaki.jiuwu.craft.attribute.api.event.PlayerResourceConsumeEvent}.
     *
     * <p>The balance is checked twice: once against {@code amount} before the event, and again against the
     * possibly rewritten amount a listener supplied, so a listener raising the cost cannot overdraw the pool.
     * A successful result means the new value was written; it does not imply the amount charged equals
     * {@code amount}, since a listener may have changed it.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player     the player to charge; {@code null} yields {@code INVALID_INPUT} and offline yields
     *                   {@code TARGET_OFFLINE}
     * @param resourceId the resource to charge; blank yields {@code INVALID_INPUT} and an unknown id yields
     *                   {@code NOT_FOUND}
     * @param amount     the amount to consume; must be finite and non-negative, so {@code NaN}, infinity and
     *                   negatives yield {@code INVALID_INPUT}. Zero is a legitimate no-cost charge.
     * @return success, {@code NOT_FOUND} when the player has no state for that resource,
     *         {@code REJECTED} when the current value is below the amount to charge, {@code CANCELLED} when a
     *         listener cancels the event, {@code INVALID_INPUT} when a listener rewrote the amount to a
     *         non-finite or negative value, or {@code INTERNAL_ERROR} when the write threw
     */
    @NotNull
    EmakiResult<Unit> consumeResource(@Nullable Player player, @Nullable String resourceId, double amount);

    /**
     * Schedules an equipment contribution refresh.
     *
     * <p><strong>Thread:</strong> any thread; the runtime dispatches onto the player's owner thread. This is
     * fire-and-forget rather than an async entry point: it returns as soon as the request is queued and hands
     * back no future, so success means "refresh requested", not "refresh finished". Requests already pending
     * for the same player are coalesced, and the queued task is dropped if the player becomes unusable or the
     * task is retired during shutdown drain, in which case no refresh runs and nothing is reported back.
     *
     * @param player the player whose equipment contributions are recomputed; {@code null} yields
     *               {@code INVALID_INPUT} and offline yields {@code TARGET_OFFLINE}. Note this check does not
     *               require the caller to own the player's thread.
     * @return success once the refresh has been requested or folded into a pending one, or
     *         {@code INTERNAL_ERROR} when scheduling itself threw
     */
    @NotNull
    EmakiResult<Unit> scheduleEquipmentSync(@Nullable Player player);

    /**
     * Calculates damage without applying it or firing the damage-application event.
     *
     * <p>Use this for previews and tooltips. Because nothing is applied, repeated calls are side-effect free.
     *
     * <p><strong>Thread:</strong> the owner thread of both live combatants.
     *
     * @param attacker     the attacking entity, or {@code null} for damage with no attacker; when non-null it
     *                     must be owned by the calling thread or the call fails with {@code WRONG_THREAD}
     * @param target       the entity being evaluated; {@code null} yields {@code INVALID_INPUT}, a dead or
     *                     invalid target yields {@code REJECTED}, and an offline player target yields
     *                     {@code TARGET_OFFLINE}
     * @param damageTypeId the damage type to use; blank falls back to the configured default type instead of
     *                     failing, while a non-blank unknown id yields {@code NOT_FOUND}
     * @param baseDamage   the pre-mitigation damage; must be finite and non-negative, so {@code NaN},
     *                     infinity and negatives yield {@code INVALID_INPUT}
     * @param context      optional extra context for the calculation; {@code null} is accepted as "no
     *                     context"
     * @return the calculated breakdown, or {@code INTERNAL_ERROR} when the calculation threw or produced no
     *         usable result
     */
    @NotNull
    EmakiResult<DamageResult> calculateDamage(@Nullable LivingEntity attacker,
            @Nullable LivingEntity target,
            @Nullable String damageTypeId,
            double baseDamage,
            @Nullable Map<String, Object> context);

    /**
     * Calculates and applies attribute damage.
     *
     * <p>Argument validation, id fallback and thread rules are identical to
     * {@link #calculateDamage}; the difference is that this call commits the damage and runs the
     * damage-application path.
     *
     * <p><strong>Thread:</strong> the target's owner thread, which must also own a non-null attacker.
     *
     * @param attacker     the attacking entity, or {@code null} for damage with no attacker
     * @param target       the entity being damaged
     * @param damageTypeId the damage type to use; blank falls back to the configured default type
     * @param baseDamage   the pre-mitigation damage; must be finite and non-negative
     * @param context      optional extra context for the calculation; {@code null} is accepted
     * @return success when the damage was applied, {@code REJECTED} when the runtime declined to apply it —
     *         including an immune, already-dead or otherwise unaffected target — or {@code INTERNAL_ERROR}
     *         when application threw
     */
    @NotNull
    EmakiResult<Unit> applyDamage(@Nullable LivingEntity attacker,
            @Nullable LivingEntity target,
            @Nullable String damageTypeId,
            double baseDamage,
            @Nullable Map<String, Object> context);

    /**
     * Recomputes and reapplies one player's attributes and resources immediately, rather than queueing the
     * work the way {@link #scheduleEquipmentSync(Player)} does.
     *
     * <p>Useful after a configuration reload or after an external system changed something EmakiAttribute
     * cannot observe on its own.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     *
     * @param player the player to resynchronize; {@code null} yields {@code INVALID_INPUT} and offline yields
     *               {@code TARGET_OFFLINE}
     * @return success once the resync has run, or {@code INTERNAL_ERROR} when it threw
     */
    @NotNull
    EmakiResult<Unit> resyncPlayer(@Nullable Player player);
}
