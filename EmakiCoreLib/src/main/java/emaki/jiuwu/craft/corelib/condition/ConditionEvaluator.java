package emaki.jiuwu.craft.corelib.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;

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
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        List<Boolean> results = new ArrayList<>();
        for (String condition : conditions) {
            Boolean result = evaluateSingle(condition, placeholderReplacer);
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
        String mode = Texts.lower(conditionType);
        if (Objects.equals(mode, "any_of")) {
            return results.stream().anyMatch(Boolean::booleanValue);
        }
        if (Objects.equals(mode, "at_least")) {
            int count = requiredCount == null ? 1 : requiredCount;
            return results.stream().filter(Boolean::booleanValue).count() >= count;
        }
        if (Objects.equals(mode, "exactly")) {
            int count = requiredCount == null ? 1 : requiredCount;
            return results.stream().filter(Boolean::booleanValue).count() == count;
        }
        return results.stream().allMatch(Boolean::booleanValue);
    }

}
