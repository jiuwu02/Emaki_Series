package emaki.jiuwu.craft.attribute.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record PdcReadRule(String sourceId,
        String conditionType,
        Integer requiredCount,
        List<String> conditions,
        boolean invalidAsFailure,
        int schemaVersion) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public PdcReadRule {
        sourceId = normalizeId(sourceId);
        conditionType = Texts.isBlank(conditionType) ? "all_of" : Texts.lower(conditionType);
        conditions = conditions == null ? List.of() : List.copyOf(conditions.stream().filter(Texts::isNotBlank).toList());
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source_id", sourceId);
        map.put("condition_type", conditionType);
        if (requiredCount != null) {
            map.put("required_count", requiredCount);
        }
        map.put("conditions", conditions);
        map.put("invalid_as_failure", invalidAsFailure);
        map.put("schema_version", schemaVersion);
        return map;
    }

    public static PdcReadRule fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        return new PdcReadRule(
                ConfigNodes.string(map, "source_id", ""),
                ConfigNodes.string(map, "condition_type", "all_of"),
                Numbers.tryParseInt(map.get("required_count"), null),
                Texts.asStringList(map.get("conditions")),
                ConfigNodes.bool(map, "invalid_as_failure", true),
                Numbers.tryParseInt(map.get("schema_version"), CURRENT_SCHEMA_VERSION)
        );
    }

    private static String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
