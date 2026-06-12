package emaki.jiuwu.craft.corelib.condition;

import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

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
        if ("group".equals(Texts.lower(type)) || section.contains("entries") || section.contains("conditions")) {
            return group(ConditionGroup.fromConfig(section));
        }
        String expression = firstNotBlank(
                section.getString("expression", ""),
                section.getString("condition", ""),
                section.getString("value", "")
        );
        Map<String, Object> data = ConfigNodes.entries(section);
        return new ConditionNode(type, expression, null, data);
    }

    public boolean expressionNode() {
        return "expression".equals(type) && Texts.isNotBlank(expression);
    }

    public boolean groupNode() {
        return group != null;
    }

    private static String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (Texts.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }
}
