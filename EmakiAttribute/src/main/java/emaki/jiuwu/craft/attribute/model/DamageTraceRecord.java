package emaki.jiuwu.craft.attribute.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;

public record DamageTraceRecord(
        long traceId,
        long createdAtMillis,
        UUID attackerId,
        String attackerLabel,
        UUID targetId,
        String targetLabel,
        UUID projectileId,
        String projectileLabel,
        String damageTypeId,
        String cause,
        double sourceDamage,
        double baseDamage,
        double finalDamage,
        boolean critical,
        boolean cancelled,
        boolean vanillaEventCancelled,
        boolean vanillaDamageRewritten,
        boolean applied,
        String applyMode,
        AttributeSnapshot attackerSnapshot,
        AttributeSnapshot targetSnapshot,
        Map<String, Object> variables,
        List<DamageTraceStageRecord> stages,
        List<String> events) {

    public DamageTraceRecord {
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        stages = stages == null ? List.of() : List.copyOf(stages);
        events = events == null ? List.of() : List.copyOf(events);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("traceId", traceId);
        result.put("createdAtMillis", createdAtMillis);
        result.put("attackerId", attackerId == null ? "" : attackerId.toString());
        result.put("attackerLabel", safe(attackerLabel));
        result.put("targetId", targetId == null ? "" : targetId.toString());
        result.put("targetLabel", safe(targetLabel));
        result.put("projectileId", projectileId == null ? "" : projectileId.toString());
        result.put("projectileLabel", safe(projectileLabel));
        result.put("damageTypeId", safe(damageTypeId));
        result.put("cause", safe(cause));
        result.put("sourceDamage", sourceDamage);
        result.put("baseDamage", baseDamage);
        result.put("finalDamage", finalDamage);
        result.put("critical", critical);
        result.put("cancelled", cancelled);
        result.put("vanillaEventCancelled", vanillaEventCancelled);
        result.put("vanillaDamageRewritten", vanillaDamageRewritten);
        result.put("applied", applied);
        result.put("applyMode", safe(applyMode));
        result.put("attackerSnapshot", attackerSnapshot == null ? Map.of() : attackerSnapshot.toMap());
        result.put("targetSnapshot", targetSnapshot == null ? Map.of() : targetSnapshot.toMap());
        result.put("variables", variables);
        result.put("stages", stages.stream().map(DamageTraceStageRecord::toMap).toList());
        result.put("events", events);
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
