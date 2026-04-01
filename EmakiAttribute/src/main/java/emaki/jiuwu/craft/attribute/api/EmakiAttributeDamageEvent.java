package emaki.jiuwu.craft.attribute.api;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;

import emaki.jiuwu.craft.attribute.model.DamageContext;
import emaki.jiuwu.craft.attribute.model.DamageContextVariables;
import emaki.jiuwu.craft.attribute.model.DamageResult;

public final class EmakiAttributeDamageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final DamageContext damageContext;
    private final DamageResult damageResult;
    private boolean cancelled;
    private double finalDamage;

    public EmakiAttributeDamageEvent(DamageContext damageContext, DamageResult damageResult) {
        this.damageContext = damageContext != null
                ? damageContext
                : damageResult != null && damageResult.damageContext() != null
                ? damageResult.damageContext()
                : DamageContext.legacy("", 0D, null, null, DamageContextVariables.empty());
        this.damageResult = damageResult != null
                ? damageResult
                : new DamageResult(this.damageContext.damageTypeId(), this.damageContext.baseDamage(), false, 0D, Map.of(), this.damageContext);
        this.finalDamage = this.damageResult.finalDamage();
    }

    public EmakiAttributeDamageEvent(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            String damageTypeId,
            double baseDamage,
            DamageResult damageResult) {
        this(resolveDamageContext(attacker, target, projectile, damageTypeId, baseDamage, damageResult), damageResult);
    }

    public LivingEntity getAttacker() {
        return damageContext.attacker();
    }

    public LivingEntity getTarget() {
        return damageContext.target();
    }

    public Projectile getProjectile() {
        return damageContext.projectile();
    }

    public String getDamageTypeId() {
        return damageContext.damageTypeId();
    }

    public double getBaseDamage() {
        return damageContext.baseDamage();
    }

    public boolean isCritical() {
        return damageResult != null && damageResult.critical();
    }

    public double getRoll() {
        return damageResult == null ? 0D : damageResult.roll();
    }

    public Map<String, Object> getContext() {
        return Map.copyOf(damageContext.context());
    }

    public DamageContextVariables getVariables() {
        return damageContext.variables();
    }

    public DamageContext getDamageContext() {
        return damageContext;
    }

    public DamageResult getDamageResult() {
        return damageResult;
    }

    public double getFinalDamage() {
        return finalDamage;
    }

    public void setFinalDamage(double finalDamage) {
        this.finalDamage = finalDamage;
    }

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
