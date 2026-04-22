package emaki.jiuwu.craft.attribute.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record PdcReadRule(String sourceId,
        String conditionType,
        Integer requiredCount,
        List<RuleCondition> conditions,
        boolean invalidAsFailure,
        int schemaVersion) {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    public PdcReadRule {
        sourceId = Texts.normalizeId(sourceId);
        conditionType = Texts.isBlank(conditionType) ? "all_of" : Texts.lower(conditionType);
        conditions = conditions == null ? List.of() : List.copyOf(conditions.stream().filter(java.util.Objects::nonNull).toList());
        schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("source_id", sourceId);
        map.put("condition_type", conditionType);
        if (requiredCount != null) {
            map.put("required_count", requiredCount);
        }
        if (!conditions.isEmpty()) {
            map.put("conditions", conditions.stream().map(RuleCondition::toMap).toList());
        }
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
                parseConditions(map.get("conditions")),
                ConfigNodes.bool(map, "invalid_as_failure", true),
                Numbers.tryParseInt(map.get("schema_version"), CURRENT_SCHEMA_VERSION)
        );
    }

    public boolean hasConditions() {
        return !conditions.isEmpty();
    }

    private static List<RuleCondition> parseConditions(Object raw) {
        List<RuleCondition> result = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            RuleCondition condition = RuleCondition.fromMap(entry);
            if (condition != null) {
                result.add(condition);
            }
        }
        return result;
    }

    public record RuleCondition(String type,
            String key,
            String pattern,
            String condition,
            boolean requireMatch) {

        public RuleCondition {
            type = Texts.normalizeId(type);
            key = Texts.normalizeId(key);
            pattern = Texts.toStringSafe(pattern).trim();
            condition = Texts.toStringSafe(condition).trim();
        }

        public static RuleCondition fromMap(Object raw) {
            if (raw == null) {
                return null;
            }
            String type = ConfigNodes.string(raw, "type", "");
            if (Texts.isBlank(type)) {
                return null;
            }
            String pattern = ConfigNodes.string(raw, "pattern", "");
            String condition = ConfigNodes.string(raw, "condition", "");
            return new RuleCondition(
                    type,
                    ConfigNodes.string(raw, "key", ""),
                    pattern,
                    condition,
                    ConfigNodes.bool(raw, "require_match", true)
            );
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", type);
            if (Texts.isNotBlank(key)) {
                map.put("key", key);
            }
            if (Texts.isNotBlank(pattern)) {
                map.put("pattern", pattern);
            }
            if (Texts.isNotBlank(condition)) {
                map.put("condition", condition);
            }
            map.put("require_match", requireMatch);
            return map;
        }

        public boolean hasPattern() {
            return Texts.isNotBlank(pattern);
        }

        public boolean hasCondition() {
            return Texts.isNotBlank(condition);
        }
    }
}

