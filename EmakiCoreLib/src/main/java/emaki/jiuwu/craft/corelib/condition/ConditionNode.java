package emaki.jiuwu.craft.corelib.condition;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record ConditionNode(String type,
        String expression,
        ConditionGroup group,
        Map<String, Object> data) {

    public ConditionNode {
        type = Texts.isBlank(type) ? "expression" : Texts.lower(type);
        expression = Texts.toStringSafe(expression);
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static ConditionNode expression(String expression) {
        return Texts.isBlank(expression) ? null : new ConditionNode("expression", expression, null, Map.of());
    }

    public static ConditionNode group(ConditionGroup group) {
        return group == null ? null : new ConditionNode("group", "", group, Map.of());
    }

    public static ConditionNode group(ConditionGroup group, Map<String, Object> data) {
        return group == null ? null : new ConditionNode("group", "", group, groupData(data));
    }

    private static Map<String, Object> groupData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> filtered = new LinkedHashMap<>(data);
        filtered.remove("type");
        filtered.remove("entries");
        filtered.remove("required_count");
        return filtered;
    }

    public static ConditionNode fromConfig(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof ConditionNode node) {
            return node;
        }
        if (raw instanceof String text) {
            return expression(text);
        }
        YamlSection section = ConditionGroup.asSection(raw);
        if (section == null) {
            return expression(Texts.toStringSafe(raw));
        }
        String type = section.getString("type", "expression");
        if ("group".equals(Texts.lower(type)) || section.contains("entries")) {
            return group(ConditionGroup.fromConfig(section), ConfigNodes.entries(section));
        }
        String expression = section.getString("expression", "");
        Map<String, Object> data = ConfigNodes.entries(section);
        return new ConditionNode(type, expression, null, data);
    }

    public boolean expressionNode() {
        return "expression".equals(type) && Texts.isNotBlank(expression);
    }

    public boolean groupNode() {
        return group != null;
    }

}
