package emaki.jiuwu.craft.strengthen.model;

import java.util.List;

/**
 * Lightweight API-side condition group DTO for strengthen recipes.
 *
 * <p>The runtime plugin converts this DTO to CoreLib's evaluator model when it
 * evaluates conditions. Keeping this type in the API module prevents the public
 * Strengthen API jar from depending on the CoreLib implementation jar.
 */
public record StrengthenConditionGroup(String conditionType,
        int requiredCount,
        List<StrengthenConditionNode> conditions) {

    public StrengthenConditionGroup {
        conditionType = StrengthenApiValues.isBlank(conditionType) ? "all_of" : StrengthenApiValues.lower(conditionType);
        requiredCount = Math.max(0, requiredCount);
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }

    public static StrengthenConditionGroup empty() {
        return new StrengthenConditionGroup("all_of", 0, List.of());
    }

    public boolean emptyGroup() {
        return conditions.isEmpty();
    }

    public List<String> expressionLines() {
        return conditions.stream()
                .filter(StrengthenConditionNode::expressionNode)
                .map(StrengthenConditionNode::expression)
                .filter(StrengthenApiValues::isNotBlank)
                .toList();
    }
}
