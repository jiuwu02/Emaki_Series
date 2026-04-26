package emaki.jiuwu.craft.corelib.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * 处理布尔表达式的求值，包括比较运算、逻辑运算（&&、||、!）和字面量解析。
 */
final class BooleanExpressionEvaluator {

    static final List<String> BOOLEAN_OPERATORS = List.of("<=", ">=", "==", "!=", "<", ">");

    private BooleanExpressionEvaluator() {
    }

    static Boolean evaluatePreparedBoolean(String expression) {
        String prepared = stripWrappingParentheses(expression);
        Boolean literal = parseBooleanLiteral(prepared);
        if (literal != null) {
            return literal;
        }
        if (prepared.startsWith("!")) {
            Boolean value = evaluatePreparedBoolean(prepared.substring(1).trim());
            return value == null ? null : !value;
        }
        List<String> orSegments = splitLogical(prepared, "||");
        if (orSegments.size() > 1) {
            for (String segment : orSegments) {
                Boolean value = evaluatePreparedBoolean(segment);
                if (value == null) {
                    return null;
                }
                if (value) {
                    return true;
                }
            }
            return false;
        }
        List<String> andSegments = splitLogical(prepared, "&&");
        if (andSegments.size() > 1) {
            for (String segment : andSegments) {
                Boolean value = evaluatePreparedBoolean(segment);
                if (value == null) {
                    return null;
                }
                if (!value) {
                    return false;
                }
            }
            return true;
        }
        return evaluateAtomicBoolean(prepared);
    }

    private static Boolean evaluateAtomicBoolean(String expression) {
        Comparison comparison = parseComparison(expression);
        if (comparison != null) {
            return evaluateComparison(comparison);
        }
        Double numeric = evaluateNumericIfExpression(expression);
        return numeric == null ? null : numeric > 0D;
    }

    private static Boolean evaluateComparison(Comparison comparison) {
        String leftRaw = stripWrappingParentheses(comparison.left());
        String rightRaw = stripWrappingParentheses(comparison.right());
        if (Texts.isBlank(leftRaw) || Texts.isBlank(rightRaw)) {
            return null;
        }
        Boolean leftBoolean = parseBooleanLiteral(leftRaw);
        Boolean rightBoolean = parseBooleanLiteral(rightRaw);
        if (leftBoolean != null && rightBoolean != null && isEqualityOperator(comparison.operator())) {
            return "==".equals(comparison.operator())
                    ? Objects.equals(leftBoolean, rightBoolean)
                    : !Objects.equals(leftBoolean, rightBoolean);
        }
        Double leftNumber = isQuoted(leftRaw) ? null : evaluateNumericIfExpression(leftRaw);
        Double rightNumber = isQuoted(rightRaw) ? null : evaluateNumericIfExpression(rightRaw);
        if (leftNumber != null && rightNumber != null) {
            return evaluateNumericComparison(leftNumber, comparison.operator(), rightNumber);
        }
        return evaluateStringComparison(unquote(leftRaw), comparison.operator(), unquote(rightRaw));
    }

    private static boolean evaluateNumericComparison(double left, String operator, double right) {
        return switch (operator) {
            case "<" ->
                left < right;
            case "<=" ->
                left <= right;
            case "==" ->
                Double.compare(left, right) == 0;
            case ">=" ->
                left >= right;
            case ">" ->
                left > right;
            case "!=" ->
                Double.compare(left, right) != 0;
            default ->
                false;
        };
    }

    private static boolean evaluateStringComparison(String left, String operator, String right) {
        return switch (operator) {
            case "==" ->
                Objects.equals(Texts.toStringSafe(left), Texts.toStringSafe(right));
            case "!=" ->
                !Objects.equals(Texts.toStringSafe(left), Texts.toStringSafe(right));
            default ->
                false;
        };
    }

    private static Double evaluateNumericIfExpression(String expression) {
        String prepared = stripWrappingParentheses(Texts.trim(expression));
        if (!ExpressionEngine.isPureNumericExpression(prepared)) {
            return null;
        }
        ExpressionEngine.NumericEvaluationResult result = ExpressionEngine.evaluateNumericDetailed(prepared, Map.of());
        return result.success() ? result.value() : null;
    }

    static Boolean parseBooleanLiteral(String raw) {
        String value = Texts.lower(unquote(raw)).trim();
        return switch (value) {
            case "true", "yes", "y", "on", "1" ->
                true;
            case "false", "no", "n", "off", "0" ->
                false;
            default ->
                null;
        };
    }

    static List<String> splitLogical(String expression, String operator) {
        List<String> segments = new ArrayList<>();
        if (Texts.isBlank(expression) || Texts.isBlank(operator)) {
            return segments;
        }
        StringBuilder current = new StringBuilder();
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        int depth = 0;
        for (int index = 0; index < expression.length(); index++) {
            char currentChar = expression.charAt(index);
            if (currentChar == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (currentChar == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (!singleQuoted && !doubleQuoted && currentChar == '(') {
                depth++;
            } else if (!singleQuoted && !doubleQuoted && currentChar == ')' && depth > 0) {
                depth--;
            }
            if (!singleQuoted
                    && !doubleQuoted
                    && depth == 0
                    && expression.startsWith(operator, index)) {
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

    static Comparison parseComparison(String expression) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        int depth = 0;
        for (int index = 0; index < expression.length(); index++) {
            char currentChar = expression.charAt(index);
            if (currentChar == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (currentChar == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (!singleQuoted && !doubleQuoted && currentChar == '(') {
                depth++;
            } else if (!singleQuoted && !doubleQuoted && currentChar == ')' && depth > 0) {
                depth--;
            }
            if (singleQuoted || doubleQuoted || depth != 0) {
                continue;
            }
            for (String operator : BOOLEAN_OPERATORS) {
                if (expression.startsWith(operator, index)) {
                    String left = expression.substring(0, index).trim();
                    String right = expression.substring(index + operator.length()).trim();
                    return Texts.isBlank(left) || Texts.isBlank(right) ? null : new Comparison(left, operator, right);
                }
            }
        }
        return null;
    }

    private static boolean isEqualityOperator(String operator) {
        return "==".equals(operator) || "!=".equals(operator);
    }

    static boolean isQuoted(String raw) {
        String text = Texts.trim(raw);
        return text.length() >= 2
                && ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'")));
    }

    static String unquote(String raw) {
        String text = Texts.trim(raw);
        return isQuoted(text) ? text.substring(1, text.length() - 1) : Texts.toStringSafe(raw);
    }

    static String stripWrappingParentheses(String expression) {
        String prepared = Texts.trim(expression);
        while (prepared.length() >= 2
                && prepared.charAt(0) == '('
                && prepared.charAt(prepared.length() - 1) == ')'
                && wrapsWholeExpression(prepared)) {
            prepared = prepared.substring(1, prepared.length() - 1).trim();
        }
        return prepared;
    }

    private static boolean wrapsWholeExpression(String expression) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        int depth = 0;
        for (int index = 0; index < expression.length(); index++) {
            char currentChar = expression.charAt(index);
            if (currentChar == '\'' && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (currentChar == '"' && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (!singleQuoted && !doubleQuoted && currentChar == '(') {
                depth++;
            } else if (!singleQuoted && !doubleQuoted && currentChar == ')') {
                depth--;
                if (depth == 0 && index < expression.length() - 1) {
                    return false;
                }
            }
            if (depth < 0) {
                return false;
            }
        }
        return depth == 0 && !singleQuoted && !doubleQuoted;
    }

    record Comparison(String left, String operator, String right) {

    }
}
