package emaki.jiuwu.craft.attribute.api.event;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;

import emaki.jiuwu.craft.attribute.api.model.DamageContext;
import emaki.jiuwu.craft.attribute.api.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.api.model.DamageResult;

/**
 * Fired after damage resolution and before EmakiAttribute applies the hit.
 *
 * <p>Runs synchronously on the owner thread shared by every live combatant; public damage operations reject
 * calls that do not own them. Cancelling the event or setting final damage to {@code <= 0} suppresses the
 * application. {@link #setFinalDamage(double)} overrides only the value to apply, not the immutable
 * {@link DamageContext} or {@link DamageResult}. {@link #getContext()} returns an immutable copy; entity
 * references remain live Bukkit objects.
 */
public final class EmakiAttributeDamageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DamageContext damageContext;
    private final DamageResult damageResult;
    private boolean cancelled;
    private double finalDamage;

    /**
     * Uses the result's context when {@code damageContext} is null, otherwise an empty context; a null result
     * is replaced with a zero result derived from the selected context.
     */
    public EmakiAttributeDamageEvent(DamageContext damageContext, DamageResult damageResult) {
        this.damageContext = damageContext != null
                ? damageContext
                : damageResult != null && damageResult.damageContext() != null
                ? damageResult.damageContext()
                : DamageContext.empty();
        this.damageResult = damageResult != null
                ? damageResult
                : new DamageResult(this.damageContext.damageTypeId(), this.damageContext.baseDamage(), false, 0D, Map.of(), this.damageContext);
        this.finalDamage = this.damageResult.finalDamage();
    }

    /** Missing fields inherit from {@code damageResult}'s context when available. */
    public EmakiAttributeDamageEvent(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            String damageTypeId,
            double baseDamage,
            DamageResult damageResult) {
        this(resolveDamageContext(attacker, target, projectile, damageTypeId, baseDamage, damageResult), damageResult);
    }

    /** {@return the attacking entity, or {@code null} if there is none} */
    public LivingEntity getAttacker() {
        return damageContext.attacker();
    }

    /** {@return the entity being damaged, or {@code null} if unknown} */
    public LivingEntity getTarget() {
        return damageContext.target();
    }

    /** {@return the projectile that caused the hit, or {@code null} for melee} */
    public Projectile getProjectile() {
        return damageContext.projectile();
    }

    /** {@return the EmakiAttribute damage type id for this hit} */
    public String getDamageTypeId() {
        return damageContext.damageTypeId();
    }

    /** {@return the base damage before EmakiAttribute resolution} */
    public double getBaseDamage() {
        return damageContext.baseDamage();
    }

    /** {@return whether this hit was resolved as a critical strike} */
    public boolean isCritical() {
        return damageResult != null && damageResult.critical();
    }

    /** {@return the random roll used during resolution, or {@code 0}} */
    public double getRoll() {
        return damageResult == null ? 0D : damageResult.roll();
    }

    /** {@return an immutable copy of the raw context variable map} */
    public Map<String, Object> getContext() {
        return Map.copyOf(damageContext.context());
    }

    /** {@return the structured context variables of this hit} */
    public DamageContextVariables getVariables() {
        return damageContext.variables();
    }

    /** {@return the full damage context describing this hit} */
    public DamageContext getDamageContext() {
        return damageContext;
    }

    /** {@return the resolved damage result for this hit} */
    public DamageResult getDamageResult() {
        return damageResult;
    }

    /** {@return the damage that will be applied unless changed or cancelled} */
    public double getFinalDamage() {
        return finalDamage;
    }

    /**
     * Overrides the damage that will be applied after the event.
     *
     * @param finalDamage the new final damage; values {@code <= 0} suppress the
     *                    hit
     */
    public void setFinalDamage(double finalDamage) {
        this.finalDamage = finalDamage;
    }

    /** {@return the originating Bukkit damage cause, or {@code null}} */
    public EntityDamageEvent.DamageCause getCause() {
        return damageContext.cause();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** {@return the shared handler list for this event type} */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    private static DamageContext resolveDamageContext(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            String damageTypeId,
            double baseDamage,
            DamageResult damageResult) {
        DamageContext existing = damageResult == null ? null : damageResult.damageContext();
        if (existing == null) {
            return DamageContext.of(attacker, target, projectile, null, damageTypeId, baseDamage, baseDamage, null, null, damageResult == null ? DamageContextVariables.empty() : damageResult.variables());
        }
        return DamageContext.of(
                attacker == null ? existing.attacker() : attacker,
                target == null ? existing.target() : target,
                projectile == null ? existing.projectile() : projectile,
                existing.cause(),
                damageTypeId == null || damageTypeId.isBlank() ? existing.damageTypeId() : damageTypeId,
                existing.sourceDamage(),
                baseDamage,
                existing.attackerSnapshot(),
                existing.targetSnapshot(),
                existing.variables()
        );
    }
}
