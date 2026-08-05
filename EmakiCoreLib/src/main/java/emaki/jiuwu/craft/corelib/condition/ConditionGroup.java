package emaki.jiuwu.craft.corelib.condition;

import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record ConditionGroup(String conditionType,
        int requiredCount,
        List<ConditionNode> conditions) {

    public ConditionGroup {
        conditionType = Texts.isBlank(conditionType) ? "all_of" : Texts.lower(conditionType);
        requiredCount = Math.max(0, requiredCount);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public static ConditionGroup empty() {
        return new ConditionGroup("all_of", 0, List.of());
    }

    public static ConditionGroup of(List<String> expressions, String conditionType, int requiredCount) {
        if (expressions == null || expressions.isEmpty()) {
            return new ConditionGroup(conditionType, requiredCount, List.of());
        }
        return new ConditionGroup(
                conditionType,
                requiredCount,
                expressions.stream()
                        .filter(Texts::isNotBlank)
                        .map(ConditionNode::expression)
                        .toList()
        );
    }

    public static ConditionGroup fromConfig(Object raw) {
        return fromConfig(raw, "all_of", 0);
    }

    public static ConditionGroup fromConfig(Object raw, String defaultType, int defaultRequiredCount) {
        if (raw == null) {
            return new ConditionGroup(defaultType, defaultRequiredCount, List.of());
        }
        if (raw instanceof ConditionGroup group) {
            return group;
        }
        YamlSection section = asSection(raw);
        if (section != null) {
            Object entries = section.get("entries");
            if (entries == null) {
                return new ConditionGroup(defaultType, defaultRequiredCount, List.of());
            }
            return new ConditionGroup(
                    section.getString("type", defaultType),
                    Numbers.tryParseInt(section.get("required_count"), defaultRequiredCount),
                    parseNodes(entries)
            );
        }
        return new ConditionGroup(defaultType, defaultRequiredCount, parseNodes(raw));
    }

    public boolean emptyGroup() {
        return conditions.isEmpty();
    }

    public List<String> expressionLines() {
        return conditions.stream()
                .filter(ConditionNode::expressionNode)
                .map(ConditionNode::expression)
                .filter(Texts::isNotBlank)
                .toList();
    }

    static List<ConditionNode> parseNodes(Object raw) {
        return ConfigNodes.asObjectList(raw).stream()
                .map(ConditionNode::fromConfig)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    static YamlSection asSection(Object raw) {
        if (raw instanceof YamlSection section) {
            return section;
        }
        if (raw instanceof Map<?, ?> map) {
            return new MapYamlSection(MapYamlSection.normalizeMap(map));
        }
        return null;
    }
}
