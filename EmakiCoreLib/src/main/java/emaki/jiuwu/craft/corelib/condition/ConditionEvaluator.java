package emaki.jiuwu.craft.corelib.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;

public final class ConditionEvaluator {

    public record ParsedCondition(String left, String operator, String right) {

    }

    private static final List<String> OPERATORS = List.of("<=", ">=", "==", "!=", "<", ">");

    private ConditionEvaluator() {
    }

    public static ParsedCondition parse(String line) {
        if (Texts.isBlank(line)) {
            return null;
        }
        String trimmed = Texts.trim(line);
        for (String operator : OPERATORS) {
            int index = trimmed.indexOf(operator);
            if (index < 0) {
                continue;
            }
            String left = trimmed.substring(0, index).trim();
            String right = trimmed.substring(index + operator.length()).trim();
            if ((right.startsWith("\"") && right.endsWith("\"")) || (right.startsWith("'") && right.endsWith("'"))) {
                right = right.substring(1, right.length() - 1);
            }
            return new ParsedCondition(left, operator, right);
        }
        return null;
    }

    public static Boolean evaluateSingle(String line, Function<String, String> placeholderReplacer) {
        if (Texts.isBlank(line)) {
            return null;
        }
        String prepared = placeholderReplacer == null ? line : Texts.toStringSafe(placeholderReplacer.apply(line));
        return ExpressionEngine.evaluateBoolean(prepared);
    }

    public static boolean evaluate(List<String> conditions,
            String conditionType,
            Integer requiredCount,
            Function<String, String> placeholderReplacer,
            boolean invalidAsFailure) {
        return evaluate(ConditionGroup.of(conditions, conditionType, requiredCount == null ? 0 : requiredCount),
                placeholderReplacer,
                invalidAsFailure);
    }

    public static boolean evaluate(ConditionBlock block,
            Function<String, String> placeholderReplacer) {
        return evaluate(block, placeholderReplacer, ConditionContext.EMPTY);
    }

    public static boolean evaluate(ConditionBlock block,
            Function<String, String> placeholderReplacer,
            ConditionContext context) {
        if (block == null || !block.configured()) {
            return true;
        }
        return evaluate(block.group(), placeholderReplacer, block.invalidAsFailure(), context);
    }

    public static boolean evaluate(ConditionGroup group,
            Function<String, String> placeholderReplacer,
            boolean invalidAsFailure) {
        return evaluate(group, placeholderReplacer, invalidAsFailure, ConditionContext.EMPTY);
    }

    public static boolean evaluate(ConditionGroup group,
            Function<String, String> placeholderReplacer,
            boolean invalidAsFailure,
            ConditionContext context) {
        if (group == null || group.emptyGroup()) {
            return true;
        }
        ConditionContext safeContext = context == null ? ConditionContext.EMPTY : context;
        List<Boolean> results = new ArrayList<>();
        for (ConditionNode condition : group.conditions()) {
            Boolean result = evaluateNode(condition, placeholderReplacer, invalidAsFailure, safeContext);
            if (result == null) {
                if (invalidAsFailure) {
                    return false;
                }
                continue;
            }
            results.add(result);
        }
        if (results.isEmpty()) {
            return !invalidAsFailure;
        }
        return combine(results, group.conditionType(), group.requiredCount());
    }

    public static boolean evaluate(Object conditionsConfig,
            String conditionType,
            Integer requiredCount,
            Function<String, String> placeholderReplacer,
            boolean invalidAsFailure) {
        return evaluate(conditionsConfig, conditionType, requiredCount, placeholderReplacer, invalidAsFailure, ConditionContext.EMPTY);
    }

    public static boolean evaluate(Object conditionsConfig,
            String conditionType,
            Integer requiredCount,
            Function<String, String> placeholderReplacer,
            boolean invalidAsFailure,
            ConditionContext context) {
        ConditionGroup group = ConfigNodes.asObjectList(conditionsConfig).isEmpty()
                ? ConditionGroup.empty()
                : new ConditionGroup(conditionType, requiredCount == null ? 0 : requiredCount, ConditionGroup.parseNodes(conditionsConfig));
        return evaluate(group, placeholderReplacer, invalidAsFailure, context);
    }

    private static Boolean evaluateNode(ConditionNode condition,
            Function<String, String> placeholderReplacer,
            boolean invalidAsFailure,
            ConditionContext context) {
        if (condition == null) {
            return null;
        }
        if (condition.groupNode()) {
            return evaluate(condition.group(), placeholderReplacer, invalidAsFailure, context);
        }
        if (condition.expressionNode() || Objects.equals(condition.type(), "expression")) {
            return evaluateSingle(condition.expression(), placeholderReplacer);
        }
        if (Texts.isNotBlank(condition.expression())) {
            return evaluateSingle(condition.expression(), placeholderReplacer);
        }
        return null;
    }

    private static boolean combine(List<Boolean> results, String conditionType, int requiredCount) {
        ConditionCombineMode mode = ConditionCombineMode.fromString(conditionType);
        return switch (mode) {
            case ANY_OF -> results.stream().anyMatch(Boolean::booleanValue);
            case NONE_OF -> results.stream().noneMatch(Boolean::booleanValue);
            case AT_LEAST -> {
                int count = requiredCount <= 0 ? 1 : requiredCount;
                yield results.stream().filter(Boolean::booleanValue).count() >= count;
            }
            case EXACTLY -> {
                int count = Math.max(0, requiredCount);
                yield results.stream().filter(Boolean::booleanValue).count() == count;
            }
            case ALL_OF -> results.stream().allMatch(Boolean::booleanValue);
        };
    }

}
