package emaki.jiuwu.craft.corelib.expression;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.math.Randoms;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

public final class ExpressionEngine {

    private static final int MAX_EXPRESSION_LENGTH = 256;
    private static final int MAX_NESTED_DEPTH = 10;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{(\\w+)}");
    private static final Pattern RANGE_PATTERN = Pattern.compile("^\\s*(.+)\\s*~\\s*(.+)\\s*$");
    private static final Pattern NON_NUMERIC_EXPRESSION_PATTERN = Pattern.compile("[^0-9.\\s+\\-*/%^(),]");
    private static final Pattern DANGEROUS_CHAR_PATTERN = Pattern.compile("[`$\\\\]");
    private static final List<String> BOOLEAN_OPERATORS = List.of("<=", ">=", "==", "!=", "<", ">");
    private static final int COMPILED_CACHE_LIMIT = 256;
    private static final long COMPILED_CACHE_TTL_MILLIS = 30 * 60 * 1000L;
    private static final Map<String, Expression> GLOBAL_COMPILED_CACHE = new ConcurrentHashMap<>(256);
    private static final int GLOBAL_CACHE_LIMIT = 1024;
    private static final int DEFAULT_RANDOM_MAX_ATTEMPTS = 128;
    private static final double INTEGER_ROUNDING_EPSILON = 1.0E-9D;
    private static final ThreadLocal<LinkedHashMap<String, CachedExpression>> COMPILED_CACHE = ThreadLocal.withInitial(
            () -> new LinkedHashMap<>(COMPILED_CACHE_LIMIT, 0.75F, true)
    );
    private static final Function[] CUSTOM_FUNCTIONS = new Function[]{
        new Function("ceil", 1) {
            @Override
            public double apply(double... args) {
                return Math.ceil(args[0]);
            }
        },
        new Function("floor", 1) {
            @Override
            public double apply(double... args) {
                return Math.floor(args[0]);
            }
        },
        new Function("round", 1) {
            @Override
            public double apply(double... args) {
                return Math.round(args[0]);
            }
        },
        new Function("log10", 1) {
            @Override
            public double apply(double... args) {
                return Math.log10(args[0]);
            }
        },
        new Function("min", 2) {
            @Override
            public double apply(double... args) {
                return Math.min(args[0], args[1]);
            }
        },
        new Function("max", 2) {
            @Override
            public double apply(double... args) {
                return Math.max(args[0], args[1]);
            }
        },
        new Function("pow", 2) {
            @Override
            public double apply(double... args) {
                return Math.pow(args[0], args[1]);
            }
        }
    };

    private ExpressionEngine() {
    }

    public static double evaluate(String expression) {
        return evaluate(expression, Map.of());
    }

    public static double evaluate(String expression, Map<String, ?> variables) {
        Double value = evaluateNumberOrNull(expression, variables);
        return value == null ? 0D : value;
    }

    public static NumericEvaluationResult evaluateNumericDetailed(String expression) {
        return evaluateNumericDetailed(expression, Map.of());
    }

    public static NumericEvaluationResult evaluateNumericDetailed(String expression, Map<String, ?> variables) {
        return evaluateNumberDetailed(expression, NumericEvaluationScope.of(variables), 0);
    }

    public static String evaluateString(String expression) {
        return evaluateString(expression, Map.of());
    }

    public static String evaluateString(String expression, Map<String, ?> variables) {
        if (expression == null) {
            return "";
        }
        if (!validateExpressionLength(expression) || containsDangerousChars(expression)) {
            return "";
        }
        String prepared = replaceVariables(expression, variables);
        if (!validateExpressionLength(prepared) || containsDangerousChars(prepared)) {
            return "";
        }
        return unquote(prepared);
    }

    public static Boolean evaluateBoolean(String expression) {
        return evaluateBoolean(expression, Map.of());
    }

    public static Boolean evaluateBoolean(String expression, Map<String, ?> variables) {
        String prepared = prepareExpression(expression, variables);
        if (prepared == null) {
            return null;
        }
        return evaluatePreparedBoolean(prepared);
    }

    public static boolean evaluateBoolean(String expression, Map<String, ?> variables, boolean fallback) {
        Boolean value = evaluateBoolean(expression, variables);
        return value == null ? fallback : value;
    }

    private static Double evaluateNumberOrNull(String expression, Map<String, ?> variables) {
        NumericEvaluationResult result = evaluateNumericDetailed(expression, variables);
        return result.success() ? result.value() : null;
    }

    public static double evaluateRandomConfig(Object config) {
        return evaluateRandomConfig(config, Map.of());
    }

    public static double evaluateRandomConfig(Object config, Map<String, ?> variables) {
        NumericEvaluationResult result = evaluateRandomConfigDetailed(config, variables);
        return result.success() ? result.value() : 0D;
    }

    public static NumericEvaluationResult evaluateRandomConfigDetailed(Object config) {
        return evaluateRandomConfigDetailed(config, Map.of());
    }

    public static NumericEvaluationResult evaluateRandomConfigDetailed(Object config, Map<String, ?> variables) {
        return evaluateRandomConfigDetailed(config, NumericEvaluationScope.of(variables), 0);
    }

    public static String replaceVariables(String expression, Map<String, ?> variables) {
        if (expression == null) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return expression;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(expression);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(Texts.toStringSafe(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public static String evaluateExpressionBlock(String innerExpression, Matcher matcher, Map<String, ?> variables) {
        String prepared = Texts.toStringSafe(innerExpression);
        if (matcher != null) {
            for (int group = 1; group <= matcher.groupCount(); group++) {
                String groupValue = matcher.group(group);
                if (groupValue != null) {
                    prepared = prepared.replace("$" + group, groupValue);
                }
            }
        }
        prepared = replaceVariables(prepared, variables);
        if (!isPureNumericExpression(prepared)) {
            Boolean booleanResult = evaluateBoolean(prepared, Map.of());
            return booleanResult == null ? evaluateString(prepared, Map.of()) : Boolean.toString(booleanResult);
        }
        try {
            double result = evaluate(prepared);
            if (Math.abs(result - Math.rint(result)) <= INTEGER_ROUNDING_EPSILON) {
                return Long.toString(Math.round(result));
            }
            return Numbers.formatNumber(result, "0.##");
        } catch (Exception e) {
            return prepared;
        }
    }

    private static String prepareExpression(String expression, Map<String, ?> variables) {
        if (Texts.isBlank(expression)) {
            return null;
        }
        if (!validateExpressionLength(expression) || containsDangerousChars(expression)) {
            return null;
        }
        String prepared = replaceVariables(expression, variables).trim();
        if (Texts.isBlank(prepared) || !validateExpressionLength(prepared) || containsDangerousChars(prepared)) {
            return null;
        }
        return prepared;
    }

    private static boolean validateExpressionLength(String expression) {
        return expression != null && expression.length() <= MAX_EXPRESSION_LENGTH;
    }

    private static boolean containsDangerousChars(String expression) {
        return expression != null && DANGEROUS_CHAR_PATTERN.matcher(expression).find();
    }

    private static boolean isPureNumericExpression(String expression) {
        if (Texts.isBlank(expression)) {
            return false;
        }
        String lowered = Texts.lower(expression)
                .replace("ceil", "")
                .replace("floor", "")
                .replace("round", "")
                .replace("log10", "")
                .replace("min", "")
                .replace("max", "")
                .replace("pow", "");
        return !NON_NUMERIC_EXPRESSION_PATTERN.matcher(lowered).find();
    }

    private static Boolean evaluatePreparedBoolean(String expression) {
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
        if (!isPureNumericExpression(prepared)) {
            return null;
        }
        return evaluateNumberOrNull(prepared, Map.of());
    }

    private static Boolean parseBooleanLiteral(String raw) {
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

    private static List<String> splitLogical(String expression, String operator) {
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

    private static Comparison parseComparison(String expression) {
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

    private static boolean isQuoted(String raw) {
        String text = Texts.trim(raw);
        return text.length() >= 2
                && ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'")));
    }

    private static String unquote(String raw) {
        String text = Texts.trim(raw);
        return isQuoted(text) ? text.substring(1, text.length() - 1) : Texts.toStringSafe(raw);
    }

    private static String stripWrappingParentheses(String expression) {
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

    private static NumericEvaluationResult evaluateNumberDetailed(String expression,
            NumericEvaluationScope scope,
            int depth) {
        if (depth > MAX_NESTED_DEPTH) {
            return NumericEvaluationResult.failure("Numeric expression exceeded maximum nested depth "
                    + MAX_NESTED_DEPTH + ".");
        }
        if (Texts.isBlank(expression)) {
            return NumericEvaluationResult.failure("Numeric expression is blank.");
        }
        if (!validateExpressionLength(expression)) {
            return NumericEvaluationResult.failure("Numeric expression is longer than "
                    + MAX_EXPRESSION_LENGTH + " characters: " + abbreviate(expression));
        }
        if (containsDangerousChars(expression)) {
            return NumericEvaluationResult.failure("Numeric expression contains unsupported characters: "
                    + abbreviate(expression));
        }
        NumericPreparation preparation = prepareNumericExpression(expression, scope, depth + 1);
        if (!preparation.issues().isEmpty()) {
            return NumericEvaluationResult.failure(preparation.issues());
        }
        String prepared = preparation.expression();
        if (!validateExpressionLength(prepared)) {
            return NumericEvaluationResult.failure("Prepared numeric expression is longer than "
                    + MAX_EXPRESSION_LENGTH + " characters: " + abbreviate(prepared));
        }
        if (containsDangerousChars(prepared)) {
            return NumericEvaluationResult.failure("Prepared numeric expression contains unsupported characters: "
                    + abbreviate(prepared));
        }
        if (!isPureNumericExpression(prepared)) {
            return NumericEvaluationResult.failure("Prepared expression is not numeric-only after variable resolution: "
                    + abbreviate(prepared));
        }
        try {
            Expression compiled = compiledExpression(prepared);
            double result = compiled.evaluate();
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                return NumericEvaluationResult.failure("Numeric expression produced a non-finite result: "
                        + abbreviate(prepared));
            }
            return NumericEvaluationResult.success(result);
        } catch (Exception exception) {
            return NumericEvaluationResult.failure("Numeric expression could not be evaluated: "
                    + abbreviate(prepared) + " (" + Texts.toStringSafe(exception.getMessage()) + ")");
        }
    }

    private static NumericPreparation prepareNumericExpression(String expression,
            NumericEvaluationScope scope,
            int depth) {
        if (scope.variables().isEmpty()) {
            return new NumericPreparation(expression, List.of());
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(expression);
        StringBuilder buffer = new StringBuilder();
        List<String> issues = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!scope.variables().containsKey(name)) {
                issues.add("Numeric expression variable '{" + name + "}' is missing.");
                matcher.appendReplacement(buffer, "0");
                continue;
            }
            NumericEvaluationResult variableResult = resolveNumericVariable(name, scope.variables().get(name),
                    scope, depth + 1);
            if (!variableResult.success()) {
                issues.addAll(variableResult.issues());
                matcher.appendReplacement(buffer, "0");
                continue;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(Double.toString(variableResult.value())));
        }
        matcher.appendTail(buffer);
        return new NumericPreparation(buffer.toString(), issues);
    }

    private static NumericEvaluationResult resolveNumericVariable(String name,
            Object rawValue,
            NumericEvaluationScope scope,
            int depth) {
        if (depth > MAX_NESTED_DEPTH) {
            return NumericEvaluationResult.failure("Numeric expression variable '{" + name
                    + "}' exceeded maximum nested depth " + MAX_NESTED_DEPTH + ".");
        }
        Double cached = scope.resolvedVariables().get(name);
        if (cached != null) {
            return NumericEvaluationResult.success(cached);
        }
        if (scope.resolvingVariables().contains(name)) {
            return NumericEvaluationResult.failure("Numeric expression variable '{" + name
                    + "}' references itself.");
        }
        scope.resolvingVariables().add(name);
        try {
            NumericEvaluationResult result = resolveNumericVariableValue(name, rawValue, scope, depth + 1);
            if (result.success()) {
                scope.resolvedVariables().put(name, result.value());
            }
            return result;
        } finally {
            scope.resolvingVariables().remove(scope.resolvingVariables().size() - 1);
        }
    }

    private static NumericEvaluationResult resolveNumericVariableValue(String name,
            Object rawValue,
            NumericEvaluationScope scope,
            int depth) {
        if (rawValue == null) {
            return NumericEvaluationResult.failure("Numeric expression variable '{" + name + "}' is null.");
        }
        if (rawValue instanceof Number number) {
            return NumericEvaluationResult.success(number.doubleValue());
        }
        if (rawValue instanceof Boolean) {
            return NumericEvaluationResult.failure("Numeric expression variable '{" + name
                    + "}' is boolean and cannot be used in a numeric expression.");
        }
        if (rawValue instanceof String text) {
            NumericEvaluationResult result = evaluateNumericTextDetailed(text, scope, depth + 1);
            if (result.success()) {
                return result;
            }
            List<String> issues = new ArrayList<>();
            issues.add("Numeric expression variable '{" + name + "}' must resolve to a number, but got string: "
                    + abbreviate(text));
            issues.addAll(result.issues());
            return NumericEvaluationResult.failure(issues);
        }
        String type = normalizedConfigType(rawValue);
        if (isStringConfigType(type) || isBooleanConfigType(type)) {
            return NumericEvaluationResult.failure("Numeric expression variable '{" + name + "}' uses "
                    + type + " config and cannot be used in a numeric expression.");
        }
        NumericEvaluationResult result = evaluateRandomConfigDetailed(rawValue, scope, depth + 1);
        if (result.success()) {
            return result;
        }
        List<String> issues = new ArrayList<>();
        issues.add("Numeric expression variable '{" + name + "}' must resolve to a numeric config.");
        issues.addAll(result.issues());
        return NumericEvaluationResult.failure(issues);
    }

    private static NumericEvaluationResult evaluateRandomConfigDetailed(Object config,
            NumericEvaluationScope scope,
            int depth) {
        if (depth > MAX_NESTED_DEPTH) {
            return NumericEvaluationResult.failure("Numeric config exceeded maximum nested depth "
                    + MAX_NESTED_DEPTH + ".");
        }
        if (config == null) {
            return NumericEvaluationResult.failure("Numeric config is missing.");
        }
        if (config instanceof Number number) {
            return NumericEvaluationResult.success(number.doubleValue());
        }
        if (config instanceof Boolean) {
            return NumericEvaluationResult.failure("Boolean value cannot be used as numeric config.");
        }
        if (config instanceof String text) {
            return evaluateNumericTextDetailed(text, scope, depth + 1);
        }

        NumericEvaluationScope scoped = scopeWithConfigVariables(config, scope);
        String type = normalizedConfigType(config);
        if (Texts.isBlank(type)) {
            Object value = ConfigNodes.get(config, "value");
            if (value != null) {
                return evaluateRandomConfigDetailed(value, scoped, depth + 1);
            }
            if (ConfigNodes.get(config, "min") != null && ConfigNodes.get(config, "max") != null) {
                return evaluateUniformDetailed(config, scoped, depth + 1);
            }
            Object expression = ConfigNodes.get(config, "expression");
            if (expression != null) {
                return evaluateNumberDetailed(Texts.toStringSafe(expression), scoped, depth + 1);
            }
            return NumericEvaluationResult.failure("Numeric config does not declare a supported type or numeric fields.");
        }
        return switch (type) {
            case "constant", "const", "fixed" ->
                evaluateRequiredChildConfig(config, "value", scoped, depth + 1, "constant");
            case "range" ->
                evaluateRangeDetailed(config, scoped, depth + 1);
            case "uniform" ->
                evaluateUniformDetailed(config, scoped, depth + 1);
            case "gaussian", "normal" ->
                evaluateGaussianDetailed(config, scoped, depth + 1);
            case "skew_normal" ->
                evaluateSkewNormalDetailed(config, scoped, depth + 1);
            case "triangle" ->
                evaluateTriangleDetailed(config, scoped, depth + 1);
            case "expression" ->
                evaluateRequiredExpressionConfig(config, scoped, depth + 1);
            case "string", "str", "text", "boolean", "bool", "flag" ->
                NumericEvaluationResult.failure("Config type '" + type
                        + "' cannot be used where a numeric config is required.");
            default ->
                NumericEvaluationResult.failure("Unsupported numeric config type '" + type
                        + "'. Supported types: constant, range, uniform, gaussian, skew_normal, triangle, expression.");
        };
    }

    private static NumericEvaluationResult evaluateNumericTextDetailed(String text,
            NumericEvaluationScope scope,
            int depth) {
        String prepared = Texts.trim(text);
        if (Texts.isBlank(prepared)) {
            return NumericEvaluationResult.failure("Numeric text is blank.");
        }
        Matcher matcher = RANGE_PATTERN.matcher(prepared);
        if (matcher.matches()) {
            NumericEvaluationResult min = evaluateNumberDetailed(matcher.group(1), scope, depth + 1);
            NumericEvaluationResult max = evaluateNumberDetailed(matcher.group(2), scope, depth + 1);
            List<String> issues = new ArrayList<>();
            issues.addAll(min.issues());
            issues.addAll(max.issues());
            if (!min.success() || !max.success()) {
                return NumericEvaluationResult.failure(issues);
            }
            return NumericEvaluationResult.success(Randoms.uniform(min.value(), max.value()));
        }
        if (Numbers.isNumeric(prepared)) {
            return NumericEvaluationResult.success(Numbers.tryParseDouble(prepared, 0D));
        }
        if (parseBooleanLiteral(prepared) != null) {
            return NumericEvaluationResult.failure("Boolean text cannot be used as numeric text: "
                    + abbreviate(prepared));
        }
        return evaluateNumberDetailed(prepared, scope, depth + 1);
    }

    private static NumericEvaluationResult evaluateRangeDetailed(Object config,
            NumericEvaluationScope scope,
            int depth) {
        Object value = ConfigNodes.get(config, "value");
        if (value != null) {
            return evaluateRandomConfigDetailed(value, scope, depth + 1);
        }
        return evaluateUniformDetailed(config, scope, depth + 1);
    }

    private static NumericEvaluationResult evaluateUniformDetailed(Object config,
            NumericEvaluationScope scope,
            int depth) {
        NumericEvaluationResult min = evaluateRequiredChildConfig(config, "min", scope, depth + 1, "uniform");
        NumericEvaluationResult max = evaluateRequiredChildConfig(config, "max", scope, depth + 1, "uniform");
        List<String> issues = new ArrayList<>();
        issues.addAll(min.issues());
        issues.addAll(max.issues());
        if (!min.success() || !max.success()) {
            return NumericEvaluationResult.failure(issues);
        }
        return NumericEvaluationResult.success(Randoms.uniform(min.value(), max.value()));
    }

    private static NumericEvaluationResult evaluateGaussianDetailed(Object config,
            NumericEvaluationScope scope,
            int depth) {
        DistributionParamsResult paramsResult = resolveDistributionParamsDetailed(config, scope, depth + 1);
        if (!paramsResult.issues().isEmpty()) {
            return NumericEvaluationResult.failure(paramsResult.issues());
        }
        DistributionParams params = paramsResult.params();
        return NumericEvaluationResult.success(Randoms.gaussian(
                params.mean(),
                params.stdDev(),
                params.min(),
                params.max(),
                params.maxAttempts()
        ));
    }

    private static NumericEvaluationResult evaluateSkewNormalDetailed(Object config,
            NumericEvaluationScope scope,
            int depth) {
        DistributionParamsResult paramsResult = resolveDistributionParamsDetailed(config, scope, depth + 1);
        OptionalNumericResult skewness = optionalNumberDetailed(ConfigNodes.get(config, "skewness"), scope, depth + 1);
        List<String> issues = new ArrayList<>();
        issues.addAll(paramsResult.issues());
        issues.addAll(skewness.issues());
        if (!issues.isEmpty()) {
            return NumericEvaluationResult.failure(issues);
        }
        DistributionParams params = paramsResult.params();
        return NumericEvaluationResult.success(Randoms.skewNormal(
                params.mean(),
                params.stdDev(),
                skewness.value() == null ? 0D : skewness.value(),
                params.min(),
                params.max(),
                params.maxAttempts()
        ));
    }

    private static NumericEvaluationResult evaluateTriangleDetailed(Object config,
            NumericEvaluationScope scope,
            int depth) {
        OptionalNumericResult mode = optionalNumberDetailed(ConfigNodes.get(config, "mode"), scope, depth + 1);
        OptionalNumericResult deviation = optionalNumberDetailed(ConfigNodes.get(config, "deviation"), scope, depth + 1);
        List<String> issues = new ArrayList<>();
        issues.addAll(mode.issues());
        issues.addAll(deviation.issues());
        if (!issues.isEmpty()) {
            return NumericEvaluationResult.failure(issues);
        }
        return NumericEvaluationResult.success(Randoms.triangle(
                mode.value() == null ? 0D : mode.value(),
                deviation.value() == null ? 1D : deviation.value()
        ));
    }

    private static NumericEvaluationResult evaluateRequiredExpressionConfig(Object config,
            NumericEvaluationScope scope,
            int depth) {
        Object expression = ConfigNodes.get(config, "expression");
        if (expression == null) {
            expression = ConfigNodes.get(config, "formula");
        }
        if (expression == null) {
            return NumericEvaluationResult.failure("Numeric expression config is missing 'expression'.");
        }
        return evaluateNumberDetailed(Texts.toStringSafe(expression), scope, depth + 1);
    }

    private static NumericEvaluationResult evaluateRequiredChildConfig(Object config,
            String key,
            NumericEvaluationScope scope,
            int depth,
            String type) {
        Object value = ConfigNodes.get(config, key);
        if (value == null) {
            return NumericEvaluationResult.failure("Numeric config type '" + type
                    + "' is missing required field '" + key + "'.");
        }
        NumericEvaluationResult result = evaluateRandomConfigDetailed(value, scope, depth + 1);
        if (result.success()) {
            return result;
        }
        List<String> issues = new ArrayList<>();
        issues.add("Numeric config field '" + key + "' for type '" + type + "' is invalid.");
        issues.addAll(result.issues());
        return NumericEvaluationResult.failure(issues);
    }

    private static OptionalNumericResult optionalNumberDetailed(Object value,
            NumericEvaluationScope scope,
            int depth) {
        if (value == null) {
            return new OptionalNumericResult(null, List.of());
        }
        NumericEvaluationResult result = evaluateRandomConfigDetailed(value, scope, depth + 1);
        return result.success()
                ? new OptionalNumericResult(result.value(), List.of())
                : new OptionalNumericResult(null, result.issues());
    }

    private static DistributionParamsResult resolveDistributionParamsDetailed(Object config,
            NumericEvaluationScope scope,
            int depth) {
        OptionalNumericResult min = optionalNumberDetailed(ConfigNodes.get(config, "min"), scope, depth + 1);
        OptionalNumericResult max = optionalNumberDetailed(ConfigNodes.get(config, "max"), scope, depth + 1);
        OptionalNumericResult mean = optionalNumberDetailed(ConfigNodes.get(config, "mean"), scope, depth + 1);
        Object stdDevValue = ConfigNodes.get(config, "std_dev");
        if (stdDevValue == null) {
            stdDevValue = ConfigNodes.get(config, "std-dev");
        }
        OptionalNumericResult stdDev = optionalNumberDetailed(stdDevValue, scope, depth + 1);
        OptionalNumericResult maxAttempts = optionalNumberDetailed(ConfigNodes.get(config, "max_attempts"),
                scope, depth + 1);
        List<String> issues = new ArrayList<>();
        issues.addAll(min.issues());
        issues.addAll(max.issues());
        issues.addAll(mean.issues());
        issues.addAll(stdDev.issues());
        issues.addAll(maxAttempts.issues());
        if (!issues.isEmpty()) {
            return new DistributionParamsResult(null, issues);
        }
        double effectiveMean = mean.value() == null
                ? min.value() != null && max.value() != null ? (min.value() + max.value()) / 2D : 0D
                : mean.value();
        double effectiveStdDev = stdDev.value() == null
                ? min.value() != null && max.value() != null ? Math.abs(max.value() - min.value()) / 6D : 1D
                : stdDev.value();
        int effectiveMaxAttempts = maxAttempts.value() == null
                ? DEFAULT_RANDOM_MAX_ATTEMPTS
                : Math.max(1, (int) Math.round(maxAttempts.value()));
        return new DistributionParamsResult(new DistributionParams(
                min.value(),
                max.value(),
                effectiveMean,
                effectiveStdDev,
                effectiveMaxAttempts
        ), List.of());
    }

    private static NumericEvaluationScope scopeWithConfigVariables(Object config, NumericEvaluationScope scope) {
        Map<String, Object> localVariables = ConfigNodes.entries(ConfigNodes.get(config, "variables"));
        if (localVariables.isEmpty()) {
            return scope;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : scope.variables().entrySet()) {
            if (Texts.isNotBlank(entry.getKey())) {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Object> entry : localVariables.entrySet()) {
            if (Texts.isNotBlank(entry.getKey())) {
                merged.put(entry.getKey(), ConfigNodes.toPlainData(entry.getValue()));
            }
        }
        return scope.withVariables(merged);
    }

    private static String normalizedConfigType(Object config) {
        return Texts.lower(ConfigNodes.string(config, "type", "")).replace('-', '_').trim();
    }

    private static boolean isStringConfigType(String type) {
        return "string".equals(type) || "str".equals(type) || "text".equals(type);
    }

    private static boolean isBooleanConfigType(String type) {
        return "boolean".equals(type) || "bool".equals(type) || "flag".equals(type);
    }

    private static String abbreviate(Object value) {
        String text = Texts.toStringSafe(value).replace('\n', ' ').replace('\r', ' ').trim();
        return text.length() <= 96 ? text : text.substring(0, 93) + "...";
    }

    private static Expression compiledExpression(String prepared) {
        long now = System.currentTimeMillis();
        LinkedHashMap<String, CachedExpression> cache = COMPILED_CACHE.get();
        CachedExpression cached = cache.get(prepared);
        Expression expression = cached == null ? null : cached.expression();
        if (expression != null && !cached.isExpired(now)) {
            cached.touch(now);
            return expression;
        }
        if (cached != null) {
            cache.remove(prepared);
        }
        // L2: check global cache
        Expression global = GLOBAL_COMPILED_CACHE.get(prepared);
        if (global != null) {
            cache.put(prepared, new CachedExpression(global, now + COMPILED_CACHE_TTL_MILLIS));
            trimCompiledCache(cache);
            return global;
        }
        Expression compiled = new ExpressionBuilder(prepared)
                .functions(CUSTOM_FUNCTIONS)
                .build();
        cache.put(prepared, new CachedExpression(compiled, now + COMPILED_CACHE_TTL_MILLIS));
        trimCompiledCache(cache);
        // Store in global cache, evict oldest entries if full
        if (GLOBAL_COMPILED_CACHE.size() >= GLOBAL_CACHE_LIMIT) {
            var iterator = GLOBAL_COMPILED_CACHE.keySet().iterator();
            int toRemove = GLOBAL_CACHE_LIMIT / 4;
            while (iterator.hasNext() && toRemove > 0) {
                iterator.next();
                iterator.remove();
                toRemove--;
            }
        }
        GLOBAL_COMPILED_CACHE.put(prepared, compiled);
        return compiled;
    }

    private static void trimCompiledCache(LinkedHashMap<String, CachedExpression> cache) {
        while (cache.size() > COMPILED_CACHE_LIMIT) {
            String eldest = cache.keySet().iterator().next();
            cache.remove(eldest);
        }
    }

    public record NumericEvaluationResult(boolean success, double value, List<String> issues) {

        public NumericEvaluationResult {
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
            if (!success && issues.isEmpty()) {
                issues = List.of("Numeric evaluation failed.");
            }
        }

        public boolean hasIssues() {
            return !issues.isEmpty();
        }

        public static NumericEvaluationResult success(double value) {
            return new NumericEvaluationResult(true, value, List.of());
        }

        public static NumericEvaluationResult failure(String issue) {
            return new NumericEvaluationResult(false, 0D, List.of(Texts.toStringSafe(issue)));
        }

        public static NumericEvaluationResult failure(List<String> issues) {
            return new NumericEvaluationResult(false, 0D, issues);
        }
    }

    private record NumericPreparation(String expression, List<String> issues) {

        private NumericPreparation {
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }
    }

    private record OptionalNumericResult(Double value, List<String> issues) {

        private OptionalNumericResult {
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }
    }

    private record DistributionParamsResult(DistributionParams params, List<String> issues) {

        private DistributionParamsResult {
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }
    }

    private record NumericEvaluationScope(Map<String, ?> variables,
            Map<String, Double> resolvedVariables,
            List<String> resolvingVariables) {

        private static NumericEvaluationScope of(Map<String, ?> variables) {
            return new NumericEvaluationScope(
                    variables == null || variables.isEmpty() ? Map.of() : variables,
                    new LinkedHashMap<>(),
                    new ArrayList<>()
            );
        }

        private NumericEvaluationScope withVariables(Map<String, ?> variables) {
            return new NumericEvaluationScope(
                    variables == null || variables.isEmpty() ? Map.of() : variables,
                    new LinkedHashMap<>(),
                    resolvingVariables
            );
        }
    }

    private record DistributionParams(Double min, Double max, double mean, double stdDev, int maxAttempts) {

    }

    private record Comparison(String left, String operator, String right) {

    }

    /**
     * 清理当前线程的表达式编译缓存。
     * 应在插件 disable 或线程池关闭时调用，防止 ThreadLocal 内存泄漏。
     */
    public static void clearThreadLocalCache() {
        COMPILED_CACHE.remove();
    }

    /**
     * 清理全局表达式编译缓存。
     * 应在插件 disable 时调用。
     */
    public static void clearGlobalCache() {
        GLOBAL_COMPILED_CACHE.clear();
    }

    private static final class CachedExpression {

        private final SoftReference<Expression> reference;
        private volatile long expiresAt;

        private CachedExpression(Expression expression, long expiresAt) {
            this.reference = new SoftReference<>(expression);
            this.expiresAt = expiresAt;
        }

        private Expression expression() {
            return reference.get();
        }

        private boolean isExpired(long now) {
            return now >= expiresAt || reference.get() == null;
        }

        private void touch(long now) {
            expiresAt = now + COMPILED_CACHE_TTL_MILLIS;
        }
    }
}
