package emaki.jiuwu.craft.attribute.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime-only diagnostic record for one damage stage. */
public record DamageTraceStageRecord(
        String stageId,
        String kind,
        String source,
        String mode,
        double input,
        double output,
        Map<String, Object> details) {

    public DamageTraceStageRecord {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stageId", safe(stageId));
        result.put("kind", safe(kind));
        result.put("source", safe(source));
        result.put("mode", safe(mode));
        result.put("input", input);
        result.put("output", output);
        result.put("details", details);
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
