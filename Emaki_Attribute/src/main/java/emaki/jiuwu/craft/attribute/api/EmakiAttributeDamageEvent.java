package emaki.jiuwu.craft.attribute.api;

import emaki.jiuwu.craft.attribute.model.DamageResult;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class EmakiAttributeDamageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity attacker;
    private final LivingEntity target;
    private final Projectile projectile;
    private final String damageTypeId;
    private final double baseDamage;
    private final boolean critical;
    private final double roll;
    private final Map<String, Object> context;
    private final DamageResult damageResult;
    private boolean cancelled;
    private double finalDamage;

    public EmakiAttributeDamageEvent(LivingEntity attacker,
                                     LivingEntity target,
                                     Projectile projectile,
                                     String damageTypeId,
                                     double baseDamage,
                                     DamageResult damageResult) {
        this.attacker = attacker;
        this.target = target;
        this.projectile = projectile;
        this.damageTypeId = damageTypeId;
        this.baseDamage = baseDamage;
        this.critical = damageResult != null && damageResult.critical();
        this.roll = damageResult == null ? 0D : damageResult.roll();
        this.context = damageResult == null ? Map.of() : new LinkedHashMap<>(damageResult.context());
        this.damageResult = damageResult;
        this.finalDamage = damageResult == null ? 0D : damageResult.finalDamage();
    }

    public LivingEntity getAttacker() {
        return attacker;
    }

    public LivingEntity getTarget() {
        return target;
    }

    public Projectile getProjectile() {
        return projectile;
    }

    public String getDamageTypeId() {
        return damageTypeId;
    }

    public double getBaseDamage() {
        return baseDamage;
    }

    public boolean isCritical() {
        return critical;
    }

    public double getRoll() {
        return roll;
    }

    public Map<String, Object> getContext() {
        return Map.copyOf(context);
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
}
