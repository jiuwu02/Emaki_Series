package emaki.jiuwu.craft.corelib.action.select;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreTargetCondition;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetConditionArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetOutcome;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.condition.ConditionCombineMode;
import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.corelib.condition.ConditionNode;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;

public final class TargetConditionEvaluator {

    private static final Map<String, ConditionCombineMode> LOGIC_ALIASES = Map.of(
            "and", ConditionCombineMode.ALL_OF,
            "or", ConditionCombineMode.ANY_OF,
            "not", ConditionCombineMode.NONE_OF);

    private final TargetConditionRegistry conditions;
    private final Consumer<String> unknownTypeReporter;
    private final Set<String> reportedTypes = ConcurrentHashMap.newKeySet();

    public TargetConditionEvaluator(TargetConditionRegistry conditions) {
        this(conditions, null);
    }

    public TargetConditionEvaluator(TargetConditionRegistry conditions,
            @Nullable Consumer<String> unknownTypeReporter) {
        this.conditions = conditions == null ? new TargetConditionRegistry() : conditions;
        this.unknownTypeReporter = unknownTypeReporter;
    }

    public boolean matches(@Nullable ConditionGroup group, boolean invalidAsFailure,
            @NotNull TargetConditionContext context) {
        return evaluateGroup(group, invalidAsFailure, context);
    }

    private boolean evaluateGroup(ConditionGroup group, boolean invalidAsFailure, TargetConditionContext context) {
        if (group == null || group.emptyGroup()) {
            return true;
        }
        List<Boolean> results = new ArrayList<>();
        for (ConditionNode node : group.conditions()) {
            CoreTargetOutcome outcome = evaluateNode(node, invalidAsFailure, context);
            if (outcome == CoreTargetOutcome.UNKNOWN) {
                if (invalidAsFailure) {
                    return false;
                }
                continue;
            }
            results.add(outcome.passed());
        }
        if (results.isEmpty()) {
            return !invalidAsFailure;
        }
        return combine(results, group.conditionType(), group.requiredCount());
    }

    private CoreTargetOutcome evaluateNode(ConditionNode node, boolean invalidAsFailure,
            TargetConditionContext context) {
        if (node == null) {
            return CoreTargetOutcome.UNKNOWN;
        }
        if (node.groupNode()) {
            return evaluateGroup(node.group(), invalidAsFailure, context)
                    ? CoreTargetOutcome.PASS
                    : CoreTargetOutcome.FAIL;
        }
        String type = Texts.lower(node.type());
        if (node.expressionNode() || "expression".equals(type)) {
            return expression(node.expression(), context);
        }
        TargetFacts facts = context.facts();
        if (BuiltinTargetConditions.contains(type)) {
            return BuiltinTargetConditions.evaluate(type, facts, arguments(node, context));
        }
        if (facts.identitySystemKnown(type)) {
            return BuiltinTargetConditions.identity(type, facts, arguments(node, context));
        }
        CoreTargetCondition custom = conditions.find(type);
        if (custom != null) {
            return context.host().test(custom, arguments(node, context));
        }
        if (Texts.isNotBlank(node.expression())) {
            return expression(node.expression(), context);
        }
        reportUnknownType(type, facts);
        return CoreTargetOutcome.UNKNOWN;
    }

    private void reportUnknownType(String type, TargetFacts facts) {
        if (unknownTypeReporter == null || Texts.isBlank(type) || !reportedTypes.add(type)) {
            return;
        }
        List<String> known = new ArrayList<>(BuiltinTargetConditions.ids());
        known.addAll(conditions.ids());
        known.addAll(facts.identitySystems());
        unknownTypeReporter.accept("Selector condition type '" + type
                + "' is unknown; targets are treated as unmatched. Known types: " + String.join(", ", known));
    }

    private CoreTargetOutcome expression(String raw, TargetConditionContext context) {
        String prepared = context.renderer().apply(TargetPlaceholders.expand(raw, context.facts()));
        if (Texts.isBlank(prepared)) {
            return CoreTargetOutcome.UNKNOWN;
        }
        Boolean evaluated = ExpressionEngine.evaluateBoolean(prepared);
        if (evaluated == null) {
            return CoreTargetOutcome.UNKNOWN;
        }
        return evaluated ? CoreTargetOutcome.PASS : CoreTargetOutcome.FAIL;
    }

    private CoreTargetConditionArguments arguments(ConditionNode node, TargetConditionContext context) {
        Map<String, Object> fields = node.data();
        if (fields.isEmpty()) {
            return CoreTargetConditionArguments.empty();
        }
        Map<String, Object> rendered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if ("entries".equals(Texts.lower(entry.getKey()))) {
                continue;
            }
            rendered.put(entry.getKey(), render(entry.getValue(), context));
        }
        return CoreTargetConditionArguments.of(rendered);
    }

    private Object render(Object value, TargetConditionContext context) {
        if (value instanceof String text) {
            return context.renderer().apply(TargetPlaceholders.expand(text, context.facts()));
        }
        if (value instanceof List<?> list) {
            List<Object> rendered = new ArrayList<>(list.size());
            for (Object element : list) {
                rendered.add(render(element, context));
            }
            return rendered;
        }
        return value;
    }

    private static boolean combine(List<Boolean> results, String conditionType, int requiredCount) {
        ConditionCombineMode mode = modeOf(conditionType);
        return switch (mode) {
            case ANY_OF -> results.stream().anyMatch(Boolean::booleanValue);
            case NONE_OF -> results.stream().noneMatch(Boolean::booleanValue);
            case AT_LEAST -> countMatches(results) >= (requiredCount <= 0 ? 1 : requiredCount);
            case EXACTLY -> countMatches(results) == Math.max(0, requiredCount);
            case ALL_OF -> results.stream().allMatch(Boolean::booleanValue);
        };
    }

    private static long countMatches(List<Boolean> results) {
        return results.stream().filter(Boolean::booleanValue).count();
    }

    private static ConditionCombineMode modeOf(String conditionType) {
        String key = Texts.lower(Texts.toStringSafe(conditionType));
        ConditionCombineMode alias = LOGIC_ALIASES.get(key);
        return alias == null ? ConditionCombineMode.fromString(key) : alias;
    }
}