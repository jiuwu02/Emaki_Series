package emaki.jiuwu.craft.attribute.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;

public record AttributeSourceTraceReport(
        UUID playerId,
        String playerName,
        long createdAtMillis,
        AttributeSnapshot snapshot,
        List<AttributeContributionTrace> contributions,
        Map<String, Object> resources) {

    public AttributeSourceTraceReport {
        contributions = contributions == null ? List.of() : List.copyOf(contributions);
        resources = resources == null ? Map.of() : Map.copyOf(resources);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("playerId", playerId == null ? "" : playerId.toString());
        result.put("playerName", playerName == null ? "" : playerName);
        result.put("createdAtMillis", createdAtMillis);
        result.put("snapshot", snapshot == null ? Map.of() : snapshot.toMap());
        result.put("contributions", contributions.stream().map(AttributeContributionTrace::toMap).toList());
        result.put("resources", resources);
        return result;
    }
}
