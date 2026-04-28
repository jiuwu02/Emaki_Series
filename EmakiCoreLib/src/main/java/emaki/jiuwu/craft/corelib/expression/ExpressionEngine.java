package emaki.jiuwu.craft.corelib.expression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([^{}\\s]+)}");
    private static final Pattern NON_NUMERIC_EXPRESSION_PATTERN = Pattern.compile("[^0-9.\\s+\\-*/%^(),]");
    private static final Pattern DANGEROUS_CHAR_PATTERN = Pattern.compile("[`$\\\\]");
    private static final double INTEGER_ROUNDING_EPSILON = 1.0E-9D;
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
        TextEvaluationResult result = evaluateStringTemplateDetailed(expression, TextEvaluationScope.of(variables), 0);
        return result.value();
    }

    public static String evaluateStringConfig(Object config) {
        return evaluateStringConfig(config, Map.of());
    }

    public static String evaluateStringConfig(Object config, Map<String, ?> variables) {
        return evaluateStringConfigDetailed(config, variables).value();
    }

    public static List<String> evaluateStringLinesConfig(Object config) {
        return evaluateStringLinesConfig(config, Map.of());
    }

    public static List<String> evaluateStringLinesConfig(Object config, Map<String, ?> variables) {
        return evaluateStringConfigDetailed(config, variables).lines();
    }

    public static TextEvaluationResult evaluateStringConfigDetailed(Object config) {
        return evaluateStringConfigDetailed(config, Map.of());
    }

    public static TextEvaluationResult evaluateStringConfigDetailed(Object config, Map<String, ?> variables) {
        return evaluateStringConfigDetailed(config, TextEvaluationScope.of(variables), 0);
    }

    public static Boolean evaluateBoolean(String expression) {
        return evaluateBoolean(expression, Map.of());
    }

    public static Boolean evaluateBoolean(String expression, Map<String, ?> variables) {
        String prepared = prepareExpression(expression, variables);
        if (prepared == null) {
            return null;
        }
        return BooleanExpressionEvaluator.evaluatePreparedBoolean(prepared);
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

    /**
     * 清理当前线程的表达式编译缓存。
     * 应在插件 disable 或线程池关闭时调用，防止 ThreadLocal 内存泄漏。
     */
    public static void clearThreadLocalCache() {
        ExpressionCache.clearThreadLocal();
    }

    /**
     * 清理全局表达式编译缓存。
     * 应在插件 disable 时调用。
     */
    public static void clearGlobalCache() {
        ExpressionCache.clearGlobal();
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

    static boolean isPureNumericExpression(String expression) {
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

    static String abbreviate(Object value) {
        String text = Texts.toStringSafe(value).replace('\n', ' ').replace('\r', ' ').trim();
        return text.length() <= 96 ? text : text.substring(0, 93) + "...";
    }

    static Boolean parseBooleanLiteral(String raw) {
        return BooleanExpressionEvaluator.parseBooleanLiteral(raw);
    }

    static NumericEvaluationResult evaluateNumberDetailed(String expression,
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
            Expression compiled = ExpressionCache.getOrCompile(prepared,
                    expr -> new ExpressionBuilder(expr).functions(CUSTOM_FUNCTIONS).build());
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
            NumericEvaluationResult result = RandomExpressionEvaluator.evaluateNumericTextDetailed(text, scope, depth + 1, MAX_NESTED_DEPTH);
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

    private static TextEvaluationResult evaluateStringConfigDetailed(Object config,
            TextEvaluationScope scope,
            int depth) {
        if (depth > MAX_NESTED_DEPTH) {
            return TextEvaluationResult.failure("Text config exceeded maximum nested depth "
                    + MAX_NESTED_DEPTH + ".");
        }
        if (config == null) {
            return TextEvaluationResult.success("");
        }
        if (config instanceof String text) {
            return evaluateStringTemplateDetailed(text, scope, depth + 1);
        }
        if (config instanceof Number number) {
            return TextEvaluationResult.success(formatTextNumber(number.doubleValue()));
        }
        if (config instanceof Boolean bool) {
            return TextEvaluationResult.success(Boolean.toString(bool));
        }
        if (config instanceof Iterable<?>) {
            return evaluateTextListDetailed(config, scope, depth + 1);
        }

        TextEvaluationScope scoped = textScopeWithConfigVariables(config, scope);
        String type = normalizedConfigType(config);
        if (Texts.isBlank(type)) {
            if (hasRandomTextLines(config)) {
                return evaluateRandomTextLinesDetailed(config, scoped, depth + 1);
            }
            Object value = firstConfigValue(config, "value", "text", "template", "expression", "formula");
            if (value != null) {
                return evaluateStringConfigDetailed(value, scoped, depth + 1);
            }
            return TextEvaluationResult.failure("Text config does not declare text value or random text lines.");
        }

        if (isRandomTextConfigType(type)) {
            return evaluateRandomTextLinesDetailed(config, scoped, depth + 1);
        }
        if (isStringConfigType(type)) {
            Object value = firstConfigValue(config, "value", "text", "template", "expression", "formula");
            return value == null
                    ? TextEvaluationResult.success("")
                    : evaluateStringConfigDetailed(value, scoped, depth + 1);
        }
        if (isBooleanConfigType(type)) {
            TextBooleanResult result = evaluateTextBooleanValue(
                    firstConfigValue(config, "value", "expression", "formula"),
                    scoped,
                    depth + 1,
                    false
            );
            return result.issues().isEmpty()
                    ? TextEvaluationResult.success(Boolean.toString(result.value()))
                    : TextEvaluationResult.failure(result.issues(), Boolean.toString(result.value()), List.of());
        }
        if (isNumericConfigType(type)) {
            NumericEvaluationResult result = evaluateRandomConfigDetailed(config,
                    NumericEvaluationScope.of(scoped.variables()),
                    depth + 1);
            return result.success()
                    ? TextEvaluationResult.success(formatTextNumber(result.value()))
                    : TextEvaluationResult.failure(result.issues());
        }
        return TextEvaluationResult.failure("Unsupported text config type '" + type
                + "'. Supported text types: string, random_text.");
    }

    private static TextEvaluationResult evaluateRandomTextLinesDetailed(Object config,
            TextEvaluationScope scope,
            int depth) {
        if (depth > MAX_NESTED_DEPTH) {
            return TextEvaluationResult.failure("Random text config exceeded maximum nested depth "
                    + MAX_NESTED_DEPTH + ".");
        }
        TextEvaluationScope scoped = textScopeWithConfigVariables(config, scope);
        List<Object> candidates = randomTextLineCandidates(config);
        if (candidates.isEmpty()) {
            return TextEvaluationResult.failure("Random text config is missing 'lines' or 'values'.");
        }

        TextIntegerResult count = evaluateTextRollCount(config, scoped, depth + 1);
        TextBooleanResult allowDuplicates = evaluateTextAllowDuplicates(config, scoped, depth + 1);
        List<String> issues = new ArrayList<>();
        issues.addAll(count.issues());
        issues.addAll(allowDuplicates.issues());
        int requestedCount = Math.max(0, count.value());
        if (!allowDuplicates.value()) {
            requestedCount = Math.min(requestedCount, candidates.size());
        }
        if (requestedCount <= 0) {
            return new TextEvaluationResult(issues.isEmpty(), "", List.of(), issues);
        }

        List<Object> selected = new ArrayList<>();
        if (allowDuplicates.value()) {
            for (int index = 0; index < requestedCount; index++) {
                selected.add(candidates.get(Randoms.randomInt(0, candidates.size() - 1)));
            }
        } else {
            List<Object> shuffled = Randoms.shuffle(candidates);
            selected.addAll(shuffled.subList(0, requestedCount));
        }

        List<String> lines = new ArrayList<>();
        for (Object rawLine : selected) {
            TextEvaluationResult lineResult = evaluateStringConfigDetailed(rawLine, scoped.fresh(), depth + 1);
            issues.addAll(lineResult.issues());
            if (lineResult.lines().isEmpty()) {
                lines.add(lineResult.value());
                continue;
            }
            lines.addAll(lineResult.lines());
        }
        String separator = Texts.toStringSafe(firstConfigValue(config, "separator", "joiner"));
        if (separator.isEmpty()) {
            separator = "\n";
        }
        return new TextEvaluationResult(issues.isEmpty(), String.join(separator, lines), lines, issues);
    }

    private static TextEvaluationResult evaluateTextListDetailed(Object config,
            TextEvaluationScope scope,
            int depth) {
        if (depth > MAX_NESTED_DEPTH) {
            return TextEvaluationResult.failure("Text list exceeded maximum nested depth " + MAX_NESTED_DEPTH + ".");
        }
        List<String> lines = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(config)) {
            TextEvaluationResult result = evaluateStringConfigDetailed(entry, scope.fresh(), depth + 1);
            issues.addAll(result.issues());
            if (result.lines().isEmpty()) {
                lines.add(result.value());
                continue;
            }
            lines.addAll(result.lines());
        }
        return new TextEvaluationResult(issues.isEmpty(), String.join("\n", lines), lines, issues);
    }

    private static TextEvaluationResult evaluateStringTemplateDetailed(String template,
            TextEvaluationScope scope,
            int depth) {
        if (depth > MAX_NESTED_DEPTH) {
            return TextEvaluationResult.failure("Text expression exceeded maximum nested depth "
                    + MAX_NESTED_DEPTH + ".");
        }
        if (template == null) {
            return TextEvaluationResult.success("");
        }
        if (!validateExpressionLength(template)) {
            return TextEvaluationResult.failure("Text expression is longer than "
                    + MAX_EXPRESSION_LENGTH + " characters: " + abbreviate(template));
        }
        if (containsDangerousChars(template)) {
            return TextEvaluationResult.failure("Text expression contains unsupported characters: "
                    + abbreviate(template));
        }
        TextPreparation preparation = prepareTextTemplate(template, scope, depth + 1);
        String prepared = BooleanExpressionEvaluator.unquote(preparation.value());
        if (!validateExpressionLength(prepared)) {
            List<String> issues = new ArrayList<>(preparation.issues());
            issues.add("Prepared text expression is longer than "
                    + MAX_EXPRESSION_LENGTH + " characters: " + abbreviate(prepared));
            return TextEvaluationResult.failure(issues, "", List.of());
        }
        if (containsDangerousChars(prepared)) {
            List<String> issues = new ArrayList<>(preparation.issues());
            issues.add("Prepared text expression contains unsupported characters: " + abbreviate(prepared));
            return TextEvaluationResult.failure(issues, "", List.of());
        }
        return new TextEvaluationResult(preparation.issues().isEmpty(), prepared, List.of(prepared),
                preparation.issues());
    }

    private static TextPreparation prepareTextTemplate(String template, TextEvaluationScope scope, int depth) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder buffer = new StringBuilder();
        List<String> issues = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!scope.variables().containsKey(name)) {
                issues.add("Text expression variable '{" + name + "}' is missing.");
                matcher.appendReplacement(buffer, "");
                continue;
            }
            TextEvaluationResult variable = resolveTextVariable(name, scope.variables().get(name), scope, depth + 1);
            issues.addAll(variable.issues());
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(variable.value()));
        }
        matcher.appendTail(buffer);
        return new TextPreparation(buffer.toString(), issues);
    }

    private static TextEvaluationResult resolveTextVariable(String name,
            Object rawValue,
            TextEvaluationScope scope,
            int depth) {
        if (depth > MAX_NESTED_DEPTH) {
            return TextEvaluationResult.failure("Text expression variable '{" + name
                    + "}' exceeded maximum nested depth " + MAX_NESTED_DEPTH + ".");
        }
        if (scope.resolvingVariables().contains(name)) {
            return TextEvaluationResult.failure("Text expression variable '{" + name + "}' references itself.");
        }
        scope.resolvingVariables().add(name);
        try {
            return resolveTextVariableValue(name, rawValue, scope, depth + 1);
        } finally {
            scope.resolvingVariables().remove(scope.resolvingVariables().size() - 1);
        }
    }

    private static TextEvaluationResult resolveTextVariableValue(String name,
            Object rawValue,
            TextEvaluationScope scope,
            int depth) {
        if (rawValue == null) {
            return TextEvaluationResult.failure("Text expression variable '{" + name + "}' is null.", "", List.of());
        }
        if (rawValue instanceof Number number) {
            return TextEvaluationResult.success(formatTextNumber(number.doubleValue()));
        }
        if (rawValue instanceof Boolean bool) {
            return TextEvaluationResult.success(Boolean.toString(bool));
        }
        if (rawValue instanceof String text) {
            return evaluateStringTemplateDetailed(text, scope, depth + 1);
        }
        String type = normalizedConfigType(rawValue);
        if (isNumericConfigType(type) || looksLikeNumericConfig(rawValue)) {
            NumericEvaluationResult result = evaluateRandomConfigDetailed(rawValue,
                    NumericEvaluationScope.of(scope.variables()),
                    depth + 1);
            if (result.success()) {
                return TextEvaluationResult.success(formatTextNumber(result.value()));
            }
            List<String> issues = new ArrayList<>();
            issues.add("Text expression variable '{" + name + "}' numeric config is invalid.");
            issues.addAll(result.issues());
            return TextEvaluationResult.failure(issues, "", List.of());
        }
        if (isBooleanConfigType(type)) {
            TextBooleanResult result = evaluateTextBooleanValue(
                    firstConfigValue(rawValue, "value", "expression", "formula"),
                    scope,
                    depth + 1,
                    false
            );
            return result.issues().isEmpty()
                    ? TextEvaluationResult.success(Boolean.toString(result.value()))
                    : TextEvaluationResult.failure(result.issues(), Boolean.toString(result.value()), List.of());
        }
        return evaluateStringConfigDetailed(rawValue, scope, depth + 1);
    }

    private static TextIntegerResult evaluateTextRollCount(Object config, TextEvaluationScope scope, int depth) {
        Object value = firstConfigValue(config, "rolls", "count", "times", "random_times", "amount");
        if (value == null) {
            return new TextIntegerResult(1, List.of());
        }
        NumericEvaluationResult result = evaluateRandomConfigDetailed(value,
                NumericEvaluationScope.of(scope.variables()),
                depth + 1);
        if (result.success()) {
            return new TextIntegerResult(Math.max(0, (int) Math.round(result.value())), List.of());
        }
        List<String> issues = new ArrayList<>();
        issues.add("Random text roll count must resolve to a number.");
        issues.addAll(result.issues());
        return new TextIntegerResult(0, issues);
    }

    private static TextBooleanResult evaluateTextAllowDuplicates(Object config, TextEvaluationScope scope, int depth) {
        Object value = firstConfigValue(config,
                "allow_duplicates",
                "allow_duplicate",
                "allow_repeat",
                "allow_repeats",
                "repeat",
                "repeatable",
                "with_replacement");
        if (value == null) {
            return new TextBooleanResult(false, List.of());
        }
        return evaluateTextBooleanValue(value, scope, depth + 1, false);
    }

    private static TextBooleanResult evaluateTextBooleanValue(Object value,
            TextEvaluationScope scope,
            int depth,
            boolean fallback) {
        if (value == null) {
            return new TextBooleanResult(fallback, List.of());
        }
        if (value instanceof Boolean bool) {
            return new TextBooleanResult(bool, List.of());
        }
        if (value instanceof Number number) {
            return new TextBooleanResult(number.doubleValue() > 0D, List.of());
        }
        Object expression = value;
        if (!(value instanceof String)) {
            TextEvaluationScope scoped = textScopeWithConfigVariables(value, scope);
            expression = firstConfigValue(value, "value", "expression", "formula");
            if (expression == null) {
                return new TextBooleanResult(fallback, List.of("Boolean text config is missing value or expression."));
            }
            scope = scoped;
        }
        Boolean result = evaluateBoolean(Texts.toStringSafe(expression), scope.variables());
        if (result == null) {
            return new TextBooleanResult(fallback, List.of("Boolean text config could not be evaluated: "
                    + abbreviate(expression)));
        }
        return new TextBooleanResult(result, List.of());
    }

    private static List<Object> randomTextLineCandidates(Object config) {
        Object value = firstConfigValue(config, "lines", "values", "options", "texts", "value");
        if (value == null) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(value)) {
            if (entry != null) {
                result.add(entry);
            }
        }
        return result;
    }

    private static boolean hasRandomTextLines(Object config) {
        return firstConfigValue(config, "lines", "values", "options", "texts") != null;
    }

    private static Object firstConfigValue(Object config, String... keys) {
        if (config == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (ConfigNodes.contains(config, key)) {
                return ConfigNodes.get(config, key);
            }
        }
        return null;
    }

    private static TextEvaluationScope textScopeWithConfigVariables(Object config, TextEvaluationScope scope) {
        return scope.withVariables(mergedConfigVariables(config, scope.variables()));
    }

    private static Map<String, ?> mergedConfigVariables(Object config, Map<String, ?> variables) {
        Map<String, Object> localVariables = ConfigNodes.entries(ConfigNodes.get(config, "variables"));
        if (localVariables.isEmpty()) {
            return variables == null || variables.isEmpty() ? Map.of() : variables;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        if (variables != null) {
            for (Map.Entry<String, ?> entry : variables.entrySet()) {
                if (Texts.isNotBlank(entry.getKey())) {
                    merged.put(entry.getKey(), entry.getValue());
                }
            }
        }
        for (Map.Entry<String, Object> entry : localVariables.entrySet()) {
            if (Texts.isNotBlank(entry.getKey())) {
                merged.put(entry.getKey(), ConfigNodes.toPlainData(entry.getValue()));
            }
        }
        return merged;
    }

    private static boolean isRandomTextConfigType(String type) {
        return "random_text".equals(type)
                || "random_text_lines".equals(type)
                || "random_lines".equals(type)
                || "random_line".equals(type)
                || "text_lines".equals(type);
    }

    private static boolean looksLikeNumericConfig(Object config) {
        if (config == null) {
            return false;
        }
        if (ConfigNodes.get(config, "min") != null && ConfigNodes.get(config, "max") != null) {
            return true;
        }
        Object value = ConfigNodes.get(config, "value");
        return value instanceof Number;
    }

    private static String formatTextNumber(double value) {
        if (Math.abs(value - Math.rint(value)) <= INTEGER_ROUNDING_EPSILON) {
            return Long.toString(Math.round(value));
        }
        return Numbers.formatNumber(value, "0.##");
    }

    private static NumericEvaluationResult evaluateRandomConfigDetailed(Object config,
            NumericEvaluationScope scope,
            int depth) {
        return RandomExpressionEvaluator.evaluateRandomConfigDetailed(config, scope, depth, MAX_NESTED_DEPTH);
    }

    static NumericEvaluationScope scopeWithConfigVariables(Object config, NumericEvaluationScope scope) {
        Map<String, ?> merged = mergedConfigVariables(config, scope.variables());
        return merged == scope.variables() ? scope : scope.withVariables(merged);
    }

    static String normalizedConfigType(Object config) {
        return Texts.lower(ConfigNodes.string(config, "type", "")).replace('-', '_').trim();
    }

    static boolean isStringConfigType(String type) {
        return "string".equals(type) || "str".equals(type) || "text".equals(type);
    }

    static boolean isBooleanConfigType(String type) {
        return "boolean".equals(type) || "bool".equals(type) || "flag".equals(type);
    }

    static boolean isNumericConfigType(String type) {
        return switch (type) {
            case "constant", "const", "fixed", "range", "uniform", "gaussian", "normal",
                    "skew_normal", "triangle", "expression" ->
                true;
            default ->
                false;
        };
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

    public record TextEvaluationResult(boolean success, String value, List<String> lines, List<String> issues) {

        public TextEvaluationResult {
            value = Texts.toStringSafe(value);
            lines = lines == null || lines.isEmpty() ? List.of() : List.copyOf(lines);
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
            if (!success && issues.isEmpty()) {
                issues = List.of("Text evaluation failed.");
            }
        }

        public boolean hasIssues() {
            return !issues.isEmpty();
        }

        public static TextEvaluationResult success(String value) {
            String safeValue = Texts.toStringSafe(value);
            return new TextEvaluationResult(true, safeValue, safeValue.isEmpty() ? List.of() : List.of(safeValue),
                    List.of());
        }

        public static TextEvaluationResult failure(String issue) {
            return new TextEvaluationResult(false, "", List.of(), List.of(Texts.toStringSafe(issue)));
        }

        public static TextEvaluationResult failure(String issue, String value, List<String> lines) {
            return new TextEvaluationResult(false, value, lines, List.of(Texts.toStringSafe(issue)));
        }

        public static TextEvaluationResult failure(List<String> issues) {
            return new TextEvaluationResult(false, "", List.of(), issues);
        }

        public static TextEvaluationResult failure(List<String> issues, String value, List<String> lines) {
            return new TextEvaluationResult(false, value, lines, issues);
        }
    }

    private record NumericPreparation(String expression, List<String> issues) {

        private NumericPreparation {
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }
    }

    private record TextPreparation(String value, List<String> issues) {

        private TextPreparation {
            value = Texts.toStringSafe(value);
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }
    }

    private record TextIntegerResult(int value, List<String> issues) {

        private TextIntegerResult {
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }
    }

    private record TextBooleanResult(boolean value, List<String> issues) {

        private TextBooleanResult {
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }
    }

    record TextEvaluationScope(Map<String, ?> variables, List<String> resolvingVariables) {

        static TextEvaluationScope of(Map<String, ?> variables) {
            return new TextEvaluationScope(
                    variables == null || variables.isEmpty() ? Map.of() : variables,
                    new ArrayList<>()
            );
        }

        TextEvaluationScope withVariables(Map<String, ?> variables) {
            return new TextEvaluationScope(
                    variables == null || variables.isEmpty() ? Map.of() : variables,
                    resolvingVariables
            );
        }

        TextEvaluationScope fresh() {
            return new TextEvaluationScope(variables, new ArrayList<>());
        }
    }

    record NumericEvaluationScope(Map<String, ?> variables,
            Map<String, Double> resolvedVariables,
            List<String> resolvingVariables) {

        static NumericEvaluationScope of(Map<String, ?> variables) {
            return new NumericEvaluationScope(
                    variables == null || variables.isEmpty() ? Map.of() : variables,
                    new LinkedHashMap<>(),
                    new ArrayList<>()
            );
        }

        NumericEvaluationScope withVariables(Map<String, ?> variables) {
            return new NumericEvaluationScope(
                    variables == null || variables.isEmpty() ? Map.of() : variables,
                    new LinkedHashMap<>(),
                    resolvingVariables
            );
        }
    }
}
