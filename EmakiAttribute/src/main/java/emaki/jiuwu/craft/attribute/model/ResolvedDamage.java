package emaki.jiuwu.craft.attribute.model;

public record ResolvedDamage(DamageContext damageContext,
        DamageResult damageResult,
        DamageTypeDefinition damageType,
        double finalDamage) {

}
