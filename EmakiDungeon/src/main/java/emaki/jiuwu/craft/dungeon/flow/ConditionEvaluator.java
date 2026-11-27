package emaki.jiuwu.craft.dungeon.flow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;

public final class ConditionEvaluator {

    public record EvalContext(UUID playerId, Map<String, Object> facts) {

        public static EvalContext of(Map<String, Object> facts) {
            return new EvalContext(null, facts == null ? Map.of() : facts);
        }

        public Object fact(String key) {
            return facts == null ? null : facts.get(key);
        }

        public boolean factFlag(String key) {
            return Boolean.TRUE.equals(fact(key));
        }

        public int factInt(String key, int fallback) {
            Object value = fact(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return value == null ? fallback : Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
    }

    public boolean evaluate(ConditionDef condition, EvalContext context) {
        if (condition == null) {
            return true;
        }
        return evaluateAll(List.of(condition), "AND", context);
    }

    public boolean evaluateAll(List<ConditionDef> conditions, String logic, EvalContext context) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        return emaki.jiuwu.craft.corelib.condition.ConditionEvaluator.evaluate(
                ConditionDef.asGroup(conditions, logic),
                null,
                true,
                toConditionContext(context)
        );
    }

    static ConditionContext toConditionContext(EvalContext context) {
        if (context == null || context.facts() == null || context.facts().isEmpty()) {
            return ConditionContext.EMPTY;
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : context.facts().entrySet()) {
            variables.put(DungeonConditions.FACT_PREFIX + entry.getKey(), entry.getValue());
        }
        return ConditionContext.of(null, null, variables);
    }
}
