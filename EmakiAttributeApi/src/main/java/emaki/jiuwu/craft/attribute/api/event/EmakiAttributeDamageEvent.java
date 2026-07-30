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
 * Fired by EmakiAttribute after it has resolved a damage calculation but before
 * the resulting damage is applied.
 *
 * <p>Listeners may inspect the attacker, target, damage type, critical state
 * and the full {@link DamageContext}/{@link DamageResult}, adjust the outgoing
 * value via {@link #setFinalDamage(double)}, or cancel the hit entirely. A
 * cancelled event (or a final damage of {@code 0}) prevents EmakiAttribute from
 * applying any damage.
 *
 * <p><strong>Thread:</strong> fired synchronously while damage is finalized on the owner thread shared by
 * the live combatants. On Paper this is the main server thread; on Folia this is the applicable entity
 * owner thread. The public damage operations reject calls that do not own every supplied combatant.
 */
public final class EmakiAttributeDamageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DamageContext damageContext;
    private final DamageResult damageResult;
    private boolean cancelled;
    private double finalDamage;

    /**
     * Creates an event from a resolved damage context and result.
     *
     * @param damageContext the context describing the hit; when {@code null}
     *                      the context carried by {@code damageResult} (or an
     *                      empty context) is used
     * @param damageResult  the computed result; when {@code null} a zero result
     *                      derived from the context is synthesized
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

    /**
     * Convenience constructor that assembles a {@link DamageContext} from the
     * individual combatants and damage metadata.
     *
     * @param attacker     the attacking entity, may be {@code null}
     * @param target       the damaged entity, may be {@code null}
     * @param projectile   the projectile involved, or {@code null} for melee
     * @param damageTypeId the EmakiAttribute damage type id
     * @param baseDamage   the base damage before resolution
     * @param damageResult the computed result, may be {@code null}
     */
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
