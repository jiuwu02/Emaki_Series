package emaki.jiuwu.craft.attribute.model;

import java.util.Map;

public record DamageRequest(DamageContext damageContext) {

    public DamageRequest {
        damageContext = damageContext == null
                ? DamageContext.empty()
                : damageContext;
    }

    public DamageRequest(String damageTypeId,
            double baseDamage,
            AttributeSnapshot attackerSnapshot,
            AttributeSnapshot targetSnapshot,
            Map<String, ?> context) {
        this(DamageContext.of(null, null, null, null, damageTypeId, baseDamage, attackerSnapshot, targetSnapshot, DamageContextVariables.from(context)));
    }

    public DamageRequest(String damageTypeId,
            double baseDamage,
            AttributeSnapshot attackerSnapshot,
            AttributeSnapshot targetSnapshot,
            DamageContextVariables variables) {
        this(DamageContext.of(null, null, null, null, damageTypeId, baseDamage, attackerSnapshot, targetSnapshot, variables));
    }

    public String damageTypeId() {
        return damageContext.damageTypeId();
    }

    public double baseDamage() {
        return damageContext.baseDamage();
    }

    public AttributeSnapshot attackerSnapshot() {
        return damageContext.attackerSnapshot();
    }

    public AttributeSnapshot targetSnapshot() {
        return damageContext.targetSnapshot();
    }

    public DamageContextVariables variables() {
        return damageContext.variables();
    }

    public Map<String, Object> context() {
        return damageContext.context();
    }
}
