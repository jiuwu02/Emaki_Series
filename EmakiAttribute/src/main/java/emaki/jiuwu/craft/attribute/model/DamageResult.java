package emaki.jiuwu.craft.attribute.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record DamageResult(String damageTypeId,
        double finalDamage,
        boolean critical,
        double roll,
        Map<String, Double> stageValues,
        DamageContext damageContext) {

    public DamageResult      {
        stageValues = stageValues == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stageValues));
    }

    public DamageContextVariables variables() {
        return damageContext == null ? DamageContextVariables.empty() : damageContext.variables();
    }

    public Map<String, Object> context() {
        return variables().asMap();
    }
}
