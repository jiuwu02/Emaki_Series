package emaki.jiuwu.craft.attribute.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record DamageResult(String damageTypeId,
                           double finalDamage,
                           boolean critical,
                           double roll,
                           Map<String, Double> stageValues,
                           Map<String, Object> context) {

    public DamageResult {
        stageValues = stageValues == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(stageValues));
        context = context == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(context));
    }
}
