package emaki.jiuwu.craft.attribute.model;

import emaki.jiuwu.craft.attribute.api.model.DamageContext;
import emaki.jiuwu.craft.attribute.api.model.DamageResult;

public record ResolvedDamage(DamageContext damageContext,
        DamageResult damageResult,
        DamageTypeDefinition damageType,
        double finalDamage) {

}
