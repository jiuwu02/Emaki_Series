package emaki.jiuwu.craft.attribute.model;

import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record DamageRequest(String damageTypeId,
                            double baseDamage,
                            AttributeSnapshot attackerSnapshot,
                            AttributeSnapshot targetSnapshot,
                            Map<String, Object> context) {

    public DamageRequest {
        damageTypeId = Texts.toStringSafe(damageTypeId).trim().toLowerCase(Locale.ROOT);
        if (context == null || context.isEmpty()) {
            context = Map.of();
        } else {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : context.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                normalized.put(entry.getKey(), entry.getValue());
            }
            context = Map.copyOf(normalized);
        }
    }
}
