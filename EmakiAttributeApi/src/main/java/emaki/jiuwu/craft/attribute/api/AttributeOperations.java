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
 * <p>All methods use the shared {@link EmakiResult} contract and return an unavailable failure while the
 * runtime bridge is absent.
 *
 * <p><strong>Thread:</strong> synchronous calls must run on every referenced entity's owner thread and do not
 * reschedule. {@link #scheduleEquipmentSync(Player)} is the only any-thread, fire-and-forget entry point.
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
     * @param player     the player to charge
     * @param resourceId the resource to charge
     * @param amount     the finite, non-negative amount; zero is a valid no-cost charge
     * @return success after writing the listener-adjusted amount; insufficient balance and cancellation are
     *         reported through the shared failure contract
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
     * @param player the player whose equipment contributions are recomputed
     * @return success once the refresh has been requested or folded into a pending request; completion is not
     *         reported
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
     * @param attacker     the attacking entity, or {@code null} for damage with no attacker
     * @param target       the entity being evaluated
     * @param damageTypeId the damage type to use; blank selects the configured default
     * @param baseDamage   the finite, non-negative pre-mitigation damage
     * @param context      optional extra context; {@code null} means no extra context
     * @return the calculated breakdown through the shared result contract
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
     * @return success only when damage was applied; an immune, dead or otherwise unaffected target is a
     *         rejected result rather than a successful zero-damage application
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
     * @param player the player to resynchronize
     * @return success once the resync has completed
     */
    @NotNull
    EmakiResult<Unit> resyncPlayer(@Nullable Player player);
}
