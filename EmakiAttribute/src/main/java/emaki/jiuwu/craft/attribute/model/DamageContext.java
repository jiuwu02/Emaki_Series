package emaki.jiuwu.craft.attribute.model;

import java.util.Locale;
import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;

import emaki.jiuwu.craft.corelib.text.Texts;

public record DamageContext(LivingEntity attacker,
        LivingEntity target,
        Projectile projectile,
        EntityDamageEvent.DamageCause cause,
        String damageTypeId,
        double sourceDamage,
        double baseDamage,
        AttributeSnapshot attackerSnapshot,
        AttributeSnapshot targetSnapshot,
        DamageContextVariables variables) {

    public DamageContext          {
        damageTypeId = normalizeId(damageTypeId);
        sourceDamage = Math.max(0D, sourceDamage);
        baseDamage = Math.max(0D, baseDamage);
        attackerSnapshot = attackerSnapshot == null ? AttributeSnapshot.empty("") : attackerSnapshot;
        targetSnapshot = targetSnapshot == null ? AttributeSnapshot.empty("") : targetSnapshot;
        variables = variables == null ? DamageContextVariables.empty() : variables;
    }

    public static DamageContext legacy(String damageTypeId,
            double baseDamage,
            AttributeSnapshot attackerSnapshot,
            AttributeSnapshot targetSnapshot,
            Map<String, ?> variables) {
        return new DamageContext(null, null, null, null, damageTypeId, baseDamage, baseDamage, attackerSnapshot, targetSnapshot, DamageContextVariables.from(variables));
    }

    public static DamageContext legacy(String damageTypeId,
            double baseDamage,
            AttributeSnapshot attackerSnapshot,
            AttributeSnapshot targetSnapshot,
            DamageContextVariables variables) {
        return new DamageContext(null, null, null, null, damageTypeId, baseDamage, baseDamage, attackerSnapshot, targetSnapshot, variables);
    }

    public static DamageContext of(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            EntityDamageEvent.DamageCause cause,
            String damageTypeId,
            double sourceDamage,
            double baseDamage,
            AttributeSnapshot attackerSnapshot,
            AttributeSnapshot targetSnapshot,
            Map<String, ?> variables) {
        return new DamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, attackerSnapshot, targetSnapshot, DamageContextVariables.from(variables));
    }

    public static DamageContext of(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            EntityDamageEvent.DamageCause cause,
            String damageTypeId,
            double sourceDamage,
            double baseDamage,
            AttributeSnapshot attackerSnapshot,
            AttributeSnapshot targetSnapshot,
            DamageContextVariables variables) {
        return new DamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, attackerSnapshot, targetSnapshot, variables);
    }

    public Map<String, Object> context() {
        return variables.asMap();
    }

    public DamageContextVariables variables() {
        return variables;
    }

    public String causeId() {
        return cause == null ? "" : normalizeId(cause.name());
    }

    public String causeName() {
        return cause == null ? "" : cause.name();
    }

    public boolean hasProjectile() {
        return projectile != null;
    }

    public DamageContext withDamageTypeId(String newDamageTypeId) {
        return new DamageContext(attacker, target, projectile, cause, newDamageTypeId, sourceDamage, baseDamage, attackerSnapshot, targetSnapshot, variables);
    }

    public DamageContext withSourceDamage(double newSourceDamage) {
        return new DamageContext(attacker, target, projectile, cause, damageTypeId, newSourceDamage, baseDamage, attackerSnapshot, targetSnapshot, variables);
    }

    public DamageContext withBaseDamage(double newBaseDamage) {
        return new DamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, newBaseDamage, attackerSnapshot, targetSnapshot, variables);
    }

    public DamageContext withSnapshots(AttributeSnapshot newAttackerSnapshot, AttributeSnapshot newTargetSnapshot) {
        return new DamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, newAttackerSnapshot, newTargetSnapshot, variables);
    }

    public DamageContext withVariables(Map<String, ?> newVariables) {
        return new DamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, attackerSnapshot, targetSnapshot, DamageContextVariables.from(newVariables));
    }

    public DamageContext withVariables(DamageContextVariables newVariables) {
        return new DamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, attackerSnapshot, targetSnapshot, newVariables);
    }

    private static String normalizeId(String value) {
        return Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
