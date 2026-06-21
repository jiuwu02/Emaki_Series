package emaki.jiuwu.craft.corelib.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ConditionEvaluator {

    private static volatile emaki.jiuwu.craft.corelib.script.js.JavaScriptConditionRegistry javaScriptConditionRegistry;

    public record ParsedCondition(String left, String operator, String right) {

    }

    private static final List<String> OPERATORS = List.of("<=", ">=", "==", "!=", "<", ">");

    private ConditionEvaluator() {
    }

    public static void installJavaScriptConditionRegistry(emaki.jiuwu.craft.corelib.script.js.JavaScriptConditionRegistry registry) {
        javaScriptConditionRegistry = registry;
    }

    public static void uninstallJavaScriptConditionRegistry(emaki.jiuwu.craft.corelib.script.js.JavaScriptConditionRegistry registry) {
        if (javaScriptConditionRegistry == registry) {
            javaScriptConditionRegistry = null;
        }
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
        if (block == null || !block.configured()) {
            return true;
        }
        return evaluate(block.group(), placeholderReplacer, block.invalidAsFailure());
    }

    public static boolean evaluate(ConditionGroup group,
            Function<String, String> placeholderReplacer,
            boolean invalidAsFailure) {
        if (group == null || group.emptyGroup()) {
            return true;
        }
        List<Boolean> results = new ArrayList<>();
        for (ConditionNode condition : group.conditions()) {
            Boolean result = evaluateNode(condition, placeholderReplacer, invalidAsFailure);
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
        ConditionGroup group = ConfigNodes.asObjectList(conditionsConfig).isEmpty()
                ? ConditionGroup.empty()
                : new ConditionGroup(conditionType, requiredCount == null ? 0 : requiredCount, ConditionGroup.parseNodes(conditionsConfig));
        return evaluate(group, placeholderReplacer, invalidAsFailure);
    }

    private static Boolean evaluateNode(ConditionNode condition,
            Function<String, String> placeholderReplacer,
            boolean invalidAsFailure) {
        if (condition == null) {
            return null;
        }
        if (condition.groupNode()) {
            return evaluate(condition.group(), placeholderReplacer, invalidAsFailure);
        }
        if (isJavaScriptConditionNode(condition)) {
            emaki.jiuwu.craft.corelib.script.js.JavaScriptConditionRegistry.ConditionResult result = evaluateJavaScriptCondition(condition);
            if (!result.valid()) {
                return invalidAsFailure ? false : null;
            }
            return result.passed();
        }
        if (condition.expressionNode() || Objects.equals(condition.type(), "expression")) {
            return evaluateSingle(condition.expression(), placeholderReplacer);
        }
        if (Texts.isNotBlank(condition.expression())) {
            return evaluateSingle(condition.expression(), placeholderReplacer);
        }
        return null;
    }

    private static boolean isJavaScriptConditionNode(ConditionNode condition) {
        if (condition == null) {
            return false;
        }
        String type = Texts.lower(condition.type());
        String expression = Texts.toStringSafe(condition.expression());
        return "js".equals(type)
                || "javascript".equals(type)
                || "script".equals(type)
                || expression.startsWith("js:")
                || expression.startsWith("condition:");
    }

    private static emaki.jiuwu.craft.corelib.script.js.JavaScriptConditionRegistry.ConditionResult evaluateJavaScriptCondition(ConditionNode condition) {
        emaki.jiuwu.craft.corelib.script.js.JavaScriptConditionRegistry registry = javaScriptConditionRegistry;
        if (registry == null) {
            return emaki.jiuwu.craft.corelib.script.js.JavaScriptConditionRegistry.ConditionResult.invalid("JavaScript condition registry is not available.");
        }
        String id = javaScriptConditionId(condition);
        Map<String, Object> args = javaScriptConditionArgs(condition);
        return registry.evaluate(id, Map.of("type", condition.type(), "expression", condition.expression()), args);
    }

    private static String javaScriptConditionId(ConditionNode condition) {
        Object rawId = condition.data().get("id");
        if (rawId == null) {
            rawId = condition.data().get("condition");
        }
        if (rawId == null) {
            rawId = condition.data().get("function");
        }
        String id = Texts.normalizeId(Texts.toStringSafe(rawId));
        if (Texts.isNotBlank(id)) {
            return id;
        }
        String expression = Texts.toStringSafe(condition.expression());
        if (expression.startsWith("js:")) {
            return Texts.normalizeId(expression.substring("js:".length()));
        }
        if (expression.startsWith("condition:")) {
            return Texts.normalizeId(expression.substring("condition:".length()));
        }
        return Texts.normalizeId(expression);
    }

    private static Map<String, Object> javaScriptConditionArgs(ConditionNode condition) {
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        Object rawArgs = condition.data().get("args");
        if (rawArgs == null) {
            rawArgs = condition.data().get("parameters");
        }
        if (rawArgs != null) {
            args.putAll(ConfigNodes.entries(rawArgs));
        }
        for (Map.Entry<String, Object> entry : condition.data().entrySet()) {
            String key = entry.getKey();
            if ("type".equals(key) || "id".equals(key) || "condition".equals(key) || "function".equals(key)
                    || "expression".equals(key) || "value".equals(key) || "args".equals(key) || "parameters".equals(key)) {
                continue;
            }
            args.putIfAbsent(key, entry.getValue());
        }
        return Map.copyOf(args);
    }

    private static boolean combine(List<Boolean> results, String conditionType, int requiredCount) {
        String mode = Texts.lower(conditionType);
        if (Objects.equals(mode, "any_of")) {
            return results.stream().anyMatch(Boolean::booleanValue);
        }
        if (Objects.equals(mode, "none_of")) {
            return results.stream().noneMatch(Boolean::booleanValue);
        }
        if (Objects.equals(mode, "at_least")) {
            int count = requiredCount <= 0 ? 1 : requiredCount;
            return results.stream().filter(Boolean::booleanValue).count() >= count;
        }
        if (Objects.equals(mode, "exactly")) {
            int count = Math.max(0, requiredCount);
            return results.stream().filter(Boolean::booleanValue).count() == count;
        }
        return results.stream().allMatch(Boolean::booleanValue);
    }

}
