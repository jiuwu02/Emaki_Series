package emaki.jiuwu.craft.attribute.model;

import java.util.Map;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageEvent;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Immutable description of a single damage interaction fed into the
 * EmakiAttribute damage pipeline.
 *
 * <p>Bundles the combatants (attacker, target, optional projectile), the Bukkit
 * damage cause, the resolved damage type id, the source and base damage values,
 * pre-collected attribute snapshots for both sides, and arbitrary
 * {@link DamageContextVariables} used by stage expressions.
 *
 * <p>Instances are immutable; use the {@code withXxx} helpers to derive a copy
 * with one field changed.
 *
 * @param attacker         the attacking entity, may be {@code null}
 * @param target           the damaged entity, may be {@code null}
 * @param projectile       the projectile involved, or {@code null} for melee
 * @param cause            the originating Bukkit damage cause, may be
 *                         {@code null}
 * @param damageTypeId     the EmakiAttribute damage type id (normalized)
 * @param sourceDamage     the raw incoming damage; clamped to {@code >= 0}
 * @param baseDamage       the base damage entering resolution; clamped to
 *                         {@code >= 0}
 * @param attackerSnapshot the attacker's attribute snapshot; never {@code null}
 * @param targetSnapshot   the target's attribute snapshot; never {@code null}
 * @param variables        extra context variables; never {@code null}
 */
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

    /**
     * Canonical constructor; normalizes the damage type id, clamps damage
     * values to be non-negative and substitutes empty defaults for {@code null}
     * snapshots/variables.
     */
    public DamageContext          {
        damageTypeId = Texts.normalizeId(damageTypeId);
        sourceDamage = Math.max(0D, sourceDamage);
        baseDamage = Math.max(0D, baseDamage);
        attackerSnapshot = attackerSnapshot == null ? AttributeSnapshot.empty("") : attackerSnapshot;
        targetSnapshot = targetSnapshot == null ? AttributeSnapshot.empty("") : targetSnapshot;
        variables = variables == null ? DamageContextVariables.empty() : variables;
    }

    /** {@return an empty context with no combatants and zero damage} */
    public static DamageContext empty() {
        return new DamageContext(
                null,
                null,
                null,
                null,
                "",
                0D,
                0D,
                AttributeSnapshot.empty(""),
                AttributeSnapshot.empty(""),
                DamageContextVariables.empty()
        );
    }

    /**
     * Builds a context where {@code sourceDamage} equals {@code baseDamage},
     * taking variables from a plain map.
     *
     * @return the new context
     */
    public static DamageContext of(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            EntityDamageEvent.DamageCause cause,
            String damageTypeId,
            double baseDamage,
            AttributeSnapshot attackerSnapshot,
            AttributeSnapshot targetSnapshot,
            Map<String, ?> variables) {
        return new DamageContext(attacker, target, projectile, cause, damageTypeId, baseDamage, baseDamage, attackerSnapshot, targetSnapshot, DamageContextVariables.from(variables));
    }

    /**
     * Builds a context where {@code sourceDamage} equals {@code baseDamage},
     * taking structured variables.
     *
     * @return the new context
     */
    public static DamageContext of(LivingEntity attacker,
            LivingEntity target,
            Projectile projectile,
            EntityDamageEvent.DamageCause cause,
            String damageTypeId,
            double baseDamage,
            AttributeSnapshot attackerSnapshot,
            AttributeSnapshot targetSnapshot,
            DamageContextVariables variables) {
        return new DamageContext(attacker, target, projectile, cause, damageTypeId, baseDamage, baseDamage, attackerSnapshot, targetSnapshot, variables);
    }

    /**
     * Builds a context with distinct source and base damage, taking variables
     * from a plain map.
     *
     * @return the new context
     */
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

    /**
     * Builds a context with distinct source and base damage, taking structured
     * variables.
     *
     * @return the new context
     */
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

    /** {@return the context variables as a plain map} */
    public Map<String, Object> context() {
        return variables.asMap();
    }

    /** {@return the structured context variables} */
    public DamageContextVariables variables() {
        return variables;
    }

    /** {@return the normalized cause id, or an empty string when absent} */
    public String causeId() {
        return cause == null ? "" : Texts.normalizeId(cause.name());
    }

    /** {@return the raw cause enum name, or an empty string when absent} */
    public String causeName() {
        return cause == null ? "" : cause.name();
    }

    /** {@return whether this hit was caused by a projectile} */
    public boolean hasProjectile() {
        return projectile != null;
    }

    /**
     * {@return a copy of this context with a different damage type id}
     *
     * @param newDamageTypeId the replacement damage type id
     */
    public DamageContext withDamageTypeId(String newDamageTypeId) {
        return new DamageContext(attacker, target, projectile, cause, newDamageTypeId, sourceDamage, baseDamage, attackerSnapshot, targetSnapshot, variables);
    }

    /**
     * {@return a copy of this context with a different source damage}
     *
     * @param newSourceDamage the replacement source damage
     */
    public DamageContext withSourceDamage(double newSourceDamage) {
        return new DamageContext(attacker, target, projectile, cause, damageTypeId, newSourceDamage, baseDamage, attackerSnapshot, targetSnapshot, variables);
    }

    /**
     * {@return a copy of this context with a different base damage}
     *
     * @param newBaseDamage the replacement base damage
     */
    public DamageContext withBaseDamage(double newBaseDamage) {
        return new DamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, newBaseDamage, attackerSnapshot, targetSnapshot, variables);
    }

    /**
     * {@return a copy of this context with replaced attribute snapshots}
     *
     * @param newAttackerSnapshot the replacement attacker snapshot
     * @param newTargetSnapshot   the replacement target snapshot
     */
    public DamageContext withSnapshots(AttributeSnapshot newAttackerSnapshot, AttributeSnapshot newTargetSnapshot) {
        return new DamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, newAttackerSnapshot, newTargetSnapshot, variables);
    }

    /**
     * {@return a copy of this context with variables taken from a plain map}
     *
     * @param newVariables the replacement variable map
     */
    public DamageContext withVariables(Map<String, ?> newVariables) {
        return new DamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, attackerSnapshot, targetSnapshot, DamageContextVariables.from(newVariables));
    }

    /**
     * {@return a copy of this context with replaced structured variables}
     *
     * @param newVariables the replacement variables
     */
    public DamageContext withVariables(DamageContextVariables newVariables) {
        return new DamageContext(attacker, target, projectile, cause, damageTypeId, sourceDamage, baseDamage, attackerSnapshot, targetSnapshot, newVariables);
    }
}
