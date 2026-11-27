package emaki.jiuwu.craft.corelib.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.api.condition.CoreConditionOutcome;

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

    public static List<ConditionFailure> failures(ConditionGroup group,
            Function<String, String> placeholderReplacer,
            boolean invalidAsFailure,
            ConditionContext context) {
        if (group == null || group.emptyGroup()) {
            return List.of();
        }
        ConditionContext safeContext = context == null ? ConditionContext.EMPTY : context;
        if (evaluate(group, placeholderReplacer, invalidAsFailure, safeContext)) {
            return List.of();
        }
        List<ConditionFailure> collected = new ArrayList<>();
        collectFailures(group, placeholderReplacer, invalidAsFailure, safeContext, collected);
        return List.copyOf(collected);
    }

    private static void collectFailures(ConditionGroup group,
            Function<String, String> placeholderReplacer,
            boolean invalidAsFailure,
            ConditionContext context,
            List<ConditionFailure> collected) {
        if (group == null || group.emptyGroup()) {
            return;
        }
        boolean noneOf = ConditionCombineMode.fromString(group.conditionType()) == ConditionCombineMode.NONE_OF;
        for (ConditionNode condition : group.conditions()) {
            if (condition == null) {
                continue;
            }
            Boolean result = evaluateNode(condition, placeholderReplacer, invalidAsFailure, context);
            boolean failed = result == null ? invalidAsFailure : (noneOf ? result : !result);
            if (!failed) {
                continue;
            }
            ConditionFailure own = failureOf(condition);
            if (!own.silent()) {
                collected.add(own);
            }
            if (condition.groupNode()) {
                collectFailures(condition.group(), placeholderReplacer, invalidAsFailure, context, collected);
            }
        }
    }

    private static ConditionFailure failureOf(ConditionNode condition) {
        Object onFail = condition.data().get("on_fail");
        if (onFail == null) {
            return ConditionFailure.of("", List.of(), false);
        }
        String message = Texts.toStringSafe(ConfigNodes.get(onFail, "message")).trim();
        List<String> actions = actionLines(ConfigNodes.get(onFail, "actions"));
        boolean blockOutput = ConfigNodes.bool(onFail, "block_output", false);
        return ConditionFailure.of(message, actions, blockOutput);
    }

    private static List<String> actionLines(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof String single) {
            return Texts.isBlank(single) ? List.of() : List.of(single);
        }
        if (raw instanceof Iterable<?> iterable) {
            List<String> lines = new ArrayList<>();
            for (Object entry : iterable) {
                String line = Texts.toStringSafe(entry);
                if (Texts.isNotBlank(line)) {
                    lines.add(line);
                }
            }
            return List.copyOf(lines);
        }
        String fallback = Texts.toStringSafe(raw);
        return Texts.isBlank(fallback) ? List.of() : List.of(fallback);
    }

    public static void installRegistry(ConditionTypeRegistry registry) {
        REGISTRY = registry;
    }

    private static volatile ConditionTypeRegistry REGISTRY;

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
        Boolean typed = evaluateTyped(condition, context);
        if (typed != null) {
            return typed;
        }
        if (Texts.isNotBlank(condition.expression())) {
            return evaluateSingle(condition.expression(), placeholderReplacer);
        }
        return null;
    }

    private static Boolean evaluateTyped(ConditionNode condition, ConditionContext context) {
        ConditionTypeRegistry registry = REGISTRY;
        if (registry == null || Texts.isBlank(condition.type())) {
            return null;
        }
        if (registry.find(condition.type()) == null) {
            return null;
        }
        CoreConditionOutcome outcome = registry.test(condition.type(), condition.data(), context);
        return switch (outcome) {
            case PASS -> Boolean.TRUE;
            case FAIL -> Boolean.FALSE;
            case UNKNOWN -> null;
        };
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