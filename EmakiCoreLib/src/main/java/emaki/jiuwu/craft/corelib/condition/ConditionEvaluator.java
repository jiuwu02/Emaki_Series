package emaki.jiuwu.craft.corelib.condition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.math.Numbers;
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
        List<String> orSegments = splitLogical(line, "||");
        if (orSegments.size() > 1) {
            for (String segment : orSegments) {
                Boolean result = evaluateAndSegment(segment, placeholderReplacer);
                if (result == null) {
                    return null;
                }
                if (result) {
                    return true;
                }
            }
            return false;
        }
        return evaluateAndSegment(line, placeholderReplacer);
    }

    private static Boolean evaluateAndSegment(String line, Function<String, String> placeholderReplacer) {
        List<String> andSegments = splitLogical(line, "&&");
        for (String segment : andSegments) {
            Boolean result = evaluateAtomic(segment, placeholderReplacer);
            if (result == null) {
                return null;
            }
            if (!result) {
                return false;
            }
        }
        return !andSegments.isEmpty();
    }

    private static Boolean evaluateAtomic(String line, Function<String, String> placeholderReplacer) {
        ParsedCondition parsed = parse(line);
        if (parsed == null) {
            return null;
        }
        String left = placeholderReplacer == null ? parsed.left() : placeholderReplacer.apply(parsed.left());
        String right = placeholderReplacer == null ? parsed.right() : placeholderReplacer.apply(parsed.right());
        if (Numbers.isNumeric(left) && Numbers.isNumeric(right)) {
            return evaluateNumeric(Numbers.tryParseDouble(left, null), parsed.operator(), Numbers.tryParseDouble(right, null));
        }
        return evaluateString(left, parsed.operator(), right);
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

    private static List<String> splitLogical(String line, String operator) {
        List<String> segments = new ArrayList<>();
        if (Texts.isBlank(line) || Texts.isBlank(operator)) {
            return segments;
        }
        StringBuilder current = new StringBuilder();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int index = 0; index < line.length(); index++) {
            char currentChar = line.charAt(index);
            if (currentChar == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (currentChar == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            }
            if (!singleQuoted
                    && !doubleQuoted
                    && line.startsWith(operator, index)) {
                segments.add(current.toString().trim());
                current.setLength(0);
                index += operator.length() - 1;
                continue;
            }
            current.append(currentChar);
        }
        segments.add(current.toString().trim());
        return segments;
    }

    private static boolean evaluateNumeric(Double left, String operator, Double right) {
        if (left == null || right == null) {
            return false;
        }
        return switch (operator) {
            case "<" ->
                left < right;
            case "<=" ->
                left <= right;
            case "==" ->
                left.doubleValue() == right.doubleValue();
            case ">=" ->
                left >= right;
            case ">" ->
                left > right;
            case "!=" ->
                left.doubleValue() != right.doubleValue();
            default ->
                false;
        };
    }

    private static boolean evaluateString(String left, String operator, String right) {
        return switch (operator) {
            case "==" ->
                Objects.equals(Texts.toStringSafe(left), Texts.toStringSafe(right));
            case "!=" ->
                !Objects.equals(Texts.toStringSafe(left), Texts.toStringSafe(right));
            default ->
                false;
        };
    }
}
