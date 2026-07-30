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

/** State-changing resource, damage and synchronization operations. */
@ApiStatus.NonExtendable
public interface AttributeOperations {

    /**
     * Consumes a resource after firing
     * {@link emaki.jiuwu.craft.attribute.api.event.PlayerResourceConsumeEvent}.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     */
    @NotNull
    EmakiResult<Unit> consumeResource(@Nullable Player player, @Nullable String resourceId, double amount);

    /**
     * Schedules an equipment contribution refresh.
     *
     * <p><strong>Thread:</strong> any thread; the runtime dispatches onto the player's owner thread.
     */
    @NotNull
    EmakiResult<Unit> scheduleEquipmentSync(@Nullable Player player);

    /**
     * Calculates damage without applying it or firing the damage-application event.
     *
     * <p><strong>Thread:</strong> the owner thread of both live combatants.
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
     * <p><strong>Thread:</strong> the target's owner thread, which must also own a non-null attacker.
     */
    @NotNull
    EmakiResult<Unit> applyDamage(@Nullable LivingEntity attacker,
            @Nullable LivingEntity target,
            @Nullable String damageTypeId,
            double baseDamage,
            @Nullable Map<String, Object> context);

    /**
     * Recomputes and reapplies one player's attributes and resources.
     *
     * <p><strong>Thread:</strong> the player's owner thread.
     */
    @NotNull
    EmakiResult<Unit> resyncPlayer(@Nullable Player player);
}
