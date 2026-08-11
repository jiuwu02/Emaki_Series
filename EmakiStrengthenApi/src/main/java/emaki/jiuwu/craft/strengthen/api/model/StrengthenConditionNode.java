package emaki.jiuwu.craft.strengthen.api.model;

import java.util.Map;

/**
 * Lightweight API-side condition node DTO for strengthen recipes.
 */
public record StrengthenConditionNode(String type,
        String expression,
        StrengthenConditionGroup group,
        Map<String, Object> data) {

    public StrengthenConditionNode {
        type = StrengthenApiValues.isBlank(type) ? "expression" : StrengthenApiValues.lower(type);
        expression = StrengthenApiValues.toStringSafe(expression);
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static StrengthenConditionNode expression(String expression) {
        return StrengthenApiValues.isBlank(expression) ? null : new StrengthenConditionNode("expression", expression, null, Map.of());
    }

    public static StrengthenConditionNode group(StrengthenConditionGroup group) {
        return group == null ? null : new StrengthenConditionNode("group", "", group, Map.of());
    }

    public boolean expressionNode() {
        return "expression".equals(type) && StrengthenApiValues.isNotBlank(expression);
    }

    public boolean groupNode() {
        return group != null;
    }
}
