package emaki.jiuwu.craft.corelib.expression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.math.Randoms;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

public final class ExpressionEngine {

    static final Pattern VARIABLE_PATTERN = Pattern.compile("%([^%\\s]+)%");
    static final double INTEGER_ROUNDING_EPSILON = 1.0E-9D;
    static final String DEFAULT_RANDOM_CHARS = "abcdefghijklmnopqrstuvwxyz";
    static final Function[] CUSTOM_FUNCTIONS = new Function[]{
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

    private static final Map<String, Function> DYNAMIC_FUNCTIONS = new LinkedHashMap<>();
    private static final Object DYNAMIC_FUNCTIONS_LOCK = new Object();

    private ExpressionEngine() {
    }

    public static boolean isBuiltinFunctionName(String name) { return ExpressionEngineDelegate.isBuiltinFunctionName(name); }

    public static boolean registerDynamicFunction(String name, Function function) {
        if (Texts.isBlank(name) || function == null || isBuiltinFunctionName(name)) {
            return false;
        }
        boolean registered;
        synchronized (DYNAMIC_FUNCTIONS_LOCK) {
            registered = DYNAMIC_FUNCTIONS.putIfAbsent(name, function) == null;
        }
        if (registered) {
            clearGlobalCache();
            clearThreadLocalCache();
        }
        return registered;
    }

    public static boolean unregisterDynamicFunction(String name) {
        if (Texts.isBlank(name)) {
            return false;
        }
        boolean removed;
        synchronized (DYNAMIC_FUNCTIONS_LOCK) {
            removed = DYNAMIC_FUNCTIONS.remove(name) != null;
        }
        if (removed) {
            clearGlobalCache();
            clearThreadLocalCache();
        }
        return removed;
    }

    static List<Function> dynamicFunctionSnapshot() {
        synchronized (DYNAMIC_FUNCTIONS_LOCK) {
            return DYNAMIC_FUNCTIONS.isEmpty() ? List.of() : List.copyOf(DYNAMIC_FUNCTIONS.values());
        }
    }

    private static Function[] mergedFunctions() { return ExpressionEngineDelegate.mergedFunctions(); }

    static boolean isNumericOnlyExpression(String expression) {
        if (Texts.isBlank(expression)) {
            return false;
        }
        List<String> dynamicNames;
        synchronized (DYNAMIC_FUNCTIONS_LOCK) {
            dynamicNames = DYNAMIC_FUNCTIONS.isEmpty() ? List.of() : List.copyOf(DYNAMIC_FUNCTIONS.keySet());
        }
        if (dynamicNames.isEmpty()) {
            return isPureNumericExpression(expression);
        }
        String lowered = Texts.lower(expression);
        for (String name : dynamicNames) {
            lowered = lowered.replace(Texts.lower(name), "");
        }
        lowered = lowered
                .replace("ceil", "")
                .replace("floor", "")
                .replace("round", "")
                .replace("log10", "")
                .replace("min", "")
                .replace("max", "")
                .replace("pow", "");
        return ExpressionRules.isNumericOnlyLowered(lowered);
    }

    public static double evaluate(String expression) { return ExpressionEngineDelegate.evaluate(expression); }

    public static double evaluate(String expression, Map<String, ?> variables) { return ExpressionEngineDelegate.evaluate(expression, variables); }

    public static Map<String, Double> resolveVariables(Map<String, Object> rawValues, Map<String, ?> context) { return ExpressionEngineDelegate.resolveVariables(rawValues, context); }

    public static Map<String, Object> resolveMixedVariables(Map<String, Object> rawValues, Map<String, ?> context) { return ExpressionEngineDelegate.resolveMixedVariables(rawValues, context); }

    public static NumericEvaluationResult evaluateNumericDetailed(String expression) { return ExpressionEngineDelegate.evaluateNumericDetailed(expression); }

    public static NumericEvaluationResult evaluateNumericDetailed(String expression, Map<String, ?> variables) { return ExpressionEngineDelegate.evaluateNumericDetailed(expression, variables); }

    public static String evaluateString(String expression) { return ExpressionEngineDelegate.evaluateString(expression); }

    public static String evaluateString(String expression, Map<String, ?> variables) { return ExpressionEngineDelegate.evaluateString(expression, variables); }

    public static String evaluateStringConfig(Object config) { return ExpressionEngineDelegate.evaluateStringConfig(config); }

    public static String evaluateStringConfig(Object config, Map<String, ?> variables) { return ExpressionEngineDelegate.evaluateStringConfig(config, variables); }

    public static List<String> evaluateStringLinesConfig(Object config) { return ExpressionEngineDelegate.evaluateStringLinesConfig(config); }

    public static List<String> evaluateStringLinesConfig(Object config, Map<String, ?> variables) { return ExpressionEngineDelegate.evaluateStringLinesConfig(config, variables); }

    public static TextEvaluationResult evaluateStringConfigDetailed(Object config) {
        return evaluateStringConfigDetailed(config, Map.of());
    }

    public static TextEvaluationResult evaluateStringConfigDetailed(Object config, Map<String, ?> variables) {
        return evaluateStringConfigDetailed(config, TextEvaluationScope.of(variables), 0);
    }

    public static Boolean evaluateBoolean(String expression) { return ExpressionEngineDelegate.evaluateBoolean(expression); }

    public static Boolean evaluateBoolean(String expression, Map<String, ?> variables) { return ExpressionEngineDelegate.evaluateBoolean(expression, variables); }

    public static boolean evaluateBoolean(String expression, Map<String, ?> variables, boolean fallback) { return ExpressionEngineDelegate.evaluateBoolean(expression, variables, fallback); }

    static Object resolveMixedVariableValue(Object raw, Map<String, ?> variables) { return ExpressionEngineDelegate.resolveMixedVariableValue(raw, variables); }

    static Object coerceResolvedTextValue(Object raw, String value) { return ExpressionEngineDelegate.coerceResolvedTextValue(raw, value); }

    static Double evaluateNumberOrNull(String expression, Map<String, ?> variables) { return ExpressionEngineDelegate.evaluateNumberOrNull(expression, variables); }

    public static double evaluateRandomConfig(Object config) { return ExpressionEngineDelegate.evaluateRandomConfig(config); }

    public static double evaluateRandomConfig(Object config, Map<String, ?> variables) { return ExpressionEngineDelegate.evaluateRandomConfig(config, variables); }

    public static NumericEvaluationResult evaluateRandomConfigDetailed(Object config) { return ExpressionEngineDelegate.evaluateRandomConfigDetailed(config); }

    public static NumericEvaluationResult evaluateRandomConfigDetailed(Object config, Map<String, ?> variables) { return ExpressionEngineDelegate.evaluateRandomConfigDetailed(config, variables); }

    public static String replaceVariables(String expression, Map<String, ?> variables) { return ExpressionEngineDelegate.replaceVariables(expression, variables); }

    public static String evaluateExpressionBlock(String innerExpression, Matcher matcher, Map<String, ?> variables) { return ExpressionEngineDelegate.evaluateExpressionBlock(innerExpression, matcher, variables); }

    public static void clearThreadLocalCache() { ExpressionEngineDelegate.clearThreadLocalCache(); }

    public static void clearGlobalCache() { ExpressionEngineDelegate.clearGlobalCache(); }

    static String prepareExpression(String expression, Map<String, ?> variables) { return ExpressionEngineDelegate.prepareExpression(expression, variables); }


    static boolean isPureNumericExpression(String expression) { return ExpressionEngineDelegate.isPureNumericExpression(expression); }

    static String abbreviate(Object value) { return ExpressionEngineDelegate.abbreviate(value); }

    static Boolean parseBooleanLiteral(String raw) { return ExpressionEngineDelegate.parseBooleanLiteral(raw); }

    static NumericEvaluationResult evaluateNumberDetailed(String expression,
            NumericEvaluationScope scope,
            int depth) {
        if (depth > ExpressionRules.MAX_NESTED_DEPTH) {
            return NumericEvaluationResult.failure("数值表达式超过最大嵌套深度 "
                    + ExpressionRules.MAX_NESTED_DEPTH + ".");
        }
        if (Texts.isBlank(expression)) {
            return NumericEvaluationResult.failure("数值表达式为空.");
        }
        if (!ExpressionEngineSupport.validateExpressionLength(expression)) {
            return NumericEvaluationResult.failure("数值表达式长度超过 "
                    + ExpressionRules.MAX_EXPRESSION_LENGTH + " 个字符: " + abbreviate(expression));
        }
        if (ExpressionEngineSupport.containsDangerousChars(expression)) {
            return NumericEvaluationResult.failure("数值表达式包含不支持的字符: "
                    + abbreviate(expression));
        }
        NumericPreparation preparation = prepareNumericExpression(expression, scope, depth + 1);
        if (!preparation.issues().isEmpty()) {
            return NumericEvaluationResult.failure(preparation.issues());
        }
        String prepared = preparation.expression();
        if (!ExpressionEngineSupport.validateExpressionLength(prepared)) {
            return NumericEvaluationResult.failure("预处理后的数值表达式长度超过 "
                    + ExpressionRules.MAX_EXPRESSION_LENGTH + " 个字符: " + abbreviate(prepared));
        }
        if (ExpressionEngineSupport.containsDangerousChars(prepared)) {
            return NumericEvaluationResult.failure("预处理后的数值表达式包含不支持的字符: "
                    + abbreviate(prepared));
        }
        if (!isNumericOnlyExpression(prepared)) {
            return NumericEvaluationResult.failure("变量解析后预处理表达式不再是纯数值: "
                    + abbreviate(prepared));
        }
        try {
            Expression compiled = ExpressionCache.getOrCompile(prepared,
                    expr -> new ExpressionBuilder(expr).functions(mergedFunctions()).build());
            double result = compiled.evaluate();
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                return NumericEvaluationResult.failure("数值表达式产生了非有限结果: "
                        + abbreviate(prepared));
            }
            return NumericEvaluationResult.success(result);
        } catch (Exception exception) {
            return NumericEvaluationResult.failure("数值表达式无法求值: "
                    + abbreviate(prepared) + " (" + Texts.toStringSafe(exception.getMessage()) + ")");
        }
    }

    static NumericPreparation prepareNumericExpression(String expression,
            NumericEvaluationScope scope,
            int depth) { return ExpressionEngineDelegate.prepareNumericExpression(expression, scope, depth); }

    static NumericEvaluationResult resolveNumericVariable(String name,
            Object rawValue,
            NumericEvaluationScope scope,
            int depth) { return ExpressionEngineDelegate.resolveNumericVariable(name, rawValue, scope, depth); }

    static NumericEvaluationResult resolveNumericVariableValue(String name,
            Object rawValue,
            NumericEvaluationScope scope,
            int depth) { return ExpressionEngineDelegate.resolveNumericVariableValue(name, rawValue, scope, depth); }

    static TextEvaluationResult evaluateStringConfigDetailed(Object config,
            TextEvaluationScope scope,
            int depth) {
        if (depth > ExpressionRules.MAX_NESTED_DEPTH) {
            return TextEvaluationResult.failure("文本配置超过最大嵌套深度 "
                    + ExpressionRules.MAX_NESTED_DEPTH + ".");
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
            if (ExpressionEngineSupport.hasRandomTextLines(config)) {
                return evaluateRandomTextLinesDetailed(config, scoped, depth + 1);
            }
            if (ExpressionEngineSupport.looksLikeNumericConfig(config)) {
                NumericEvaluationResult result = evaluateRandomConfigDetailed(config,
                        NumericEvaluationScope.of(scoped.variables()),
                        depth + 1);
                return result.success()
                        ? TextEvaluationResult.success(formatTextNumber(result.value()))
                        : TextEvaluationResult.failure(result.issues());
            }
            Object value = ConfigNodes.get(config, "value");
            if (value != null) {
                return evaluateStringConfigDetailed(value, scoped, depth + 1);
            }
            return TextEvaluationResult.failure("文本配置未声明文本值或随机文本行.");
        }

        if (ExpressionEngineSupport.isRandomTextConfigType(type)) {
            return evaluateRandomTextLinesDetailed(config, scoped, depth + 1);
        }
        if (ExpressionEngineSupport.isRandomCharConfigType(type)) {
            return evaluateRandomCharsDetailed(config, scoped, depth + 1, false);
        }
        if (ExpressionEngineSupport.isWeightedRandomCharConfigType(type)) {
            return evaluateRandomCharsDetailed(config, scoped, depth + 1, true);
        }
        if (ExpressionEngineSupport.isConditionalCharConfigType(type)) {
            return evaluateConditionalCharDetailed(config, scoped, depth + 1);
        }
        if (isStringConfigType(type)) {
            Object value = ConfigNodes.get(config, "value");
            return value == null
                    ? TextEvaluationResult.success("")
                    : evaluateStringConfigDetailed(value, scoped, depth + 1);
        }
        if (isBooleanConfigType(type)) {
            TextBooleanResult result = evaluateTextBooleanValue(
                    ConfigNodes.get(config, "value"),
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
        return TextEvaluationResult.failure("不支持的文本配置类型 '" + type
                + "'.支持的文本类型: string, random_text, random_char, weighted_random_char, conditional_char, boolean.");
    }

    static TextEvaluationResult evaluateRandomTextLinesDetailed(Object config,
            TextEvaluationScope scope,
            int depth) { return ExpressionEngineDelegate.evaluateRandomTextLinesDetailed(config, scope, depth); }

    static TextEvaluationResult evaluateRandomCharsDetailed(Object config,
            TextEvaluationScope scope,
            int depth,
            boolean weighted) {
        if (depth > ExpressionRules.MAX_NESTED_DEPTH) {
            return TextEvaluationResult.failure("随机字符配置超过最大嵌套深度 "
                    + ExpressionRules.MAX_NESTED_DEPTH + ".");
        }
        TextEvaluationScope scoped = textScopeWithConfigVariables(config, scope);
        TextCandidates candidates = randomCharCandidates(config, scoped, depth + 1);
        List<String> issues = new ArrayList<>(candidates.issues());
        if (candidates.values().isEmpty()) {
            issues.add("随机字符配置没有候选字符.");
            return TextEvaluationResult.failure(issues);
        }

        TextIntegerResult count = evaluateTextRollCount(config, scoped, depth + 1);
        TextBooleanResult allowDuplicates = evaluateTextAllowDuplicates(config, scoped, depth + 1);
        issues.addAll(count.issues());
        issues.addAll(allowDuplicates.issues());
        int requestedCount = Math.max(0, count.value());
        if (requestedCount <= 0) {
            return new TextEvaluationResult(issues.isEmpty(), "", List.of(), issues);
        }

        List<String> selected = weighted
                ? selectWeightedRandomChars(config, candidates.values(), scoped, depth + 1, requestedCount,
                        allowDuplicates.value(), issues)
                : selectRandomChars(candidates.values(), requestedCount, allowDuplicates.value());
        String separator = Texts.toStringSafe(ConfigNodes.get(config, "separator"));
        String value = String.join(separator, selected);
        return new TextEvaluationResult(issues.isEmpty(), value, value.isEmpty() ? List.of() : List.of(value), issues);
    }

    static TextEvaluationResult evaluateConditionalCharDetailed(Object config,
            TextEvaluationScope scope,
            int depth) {
        if (depth > ExpressionRules.MAX_NESTED_DEPTH) {
            return TextEvaluationResult.failure("条件字符配置超过最大嵌套深度 "
                    + ExpressionRules.MAX_NESTED_DEPTH + ".");
        }
        TextEvaluationScope scoped = textScopeWithConfigVariables(config, scope);
        List<String> issues = new ArrayList<>();
        Object rawCases = ConfigNodes.get(config, "cases");
        List<Object> cases = ConfigNodes.asObjectList(rawCases);
        if (!cases.isEmpty()) {
            for (int index = 0; index < cases.size(); index++) {
                Object entry = cases.get(index);
                if (entry == null) {
                    issues.add("索引 " + index + " 处的条件字符分支为空.");
                    continue;
                }
                Object condition = ConfigNodes.get(entry, "condition");
                if (condition == null) {
                    issues.add("索引 " + index + " 处的条件字符分支缺少 condition.");
                    continue;
                }
                TextBooleanResult conditionResult = evaluateTextBooleanValue(condition, scoped, depth + 1, false);
                if (!conditionResult.issues().isEmpty()) {
                    issues.add("索引 " + index + " 处的条件字符分支的 condition 无效.");
                    issues.addAll(conditionResult.issues());
                    continue;
                }
                if (!conditionResult.value()) {
                    continue;
                }
                Object value = ConfigNodes.get(entry, "value");
                if (value == null) {
                    issues.add("索引 " + index + " 处的条件字符分支缺少 value.");
                    return new TextEvaluationResult(false, "", List.of(), issues);
                }
                return evaluateConditionalCharValue(value, scoped, depth + 1, issues);
            }
            Object fallback = ConfigNodes.get(config, "fallback");
            if (fallback == null) {
                return new TextEvaluationResult(issues.isEmpty(), "", List.of(), issues);
            }
            return evaluateConditionalCharValue(fallback, scoped, depth + 1, issues);
        }

        Object condition = ConfigNodes.get(config, "condition");
        if (condition == null) {
            return TextEvaluationResult.failure("条件字符配置缺少 'condition' 或 'cases'.");
        }
        TextBooleanResult conditionResult = evaluateTextBooleanValue(condition, scoped, depth + 1, false);
        issues.addAll(conditionResult.issues());
        Object value = conditionResult.issues().isEmpty() && conditionResult.value()
                ? ConfigNodes.get(config, "true_value")
                : ConfigNodes.get(config, "false_value");
        if (value == null) {
            issues.add("条件字符配置缺少 "
                    + (conditionResult.issues().isEmpty() && conditionResult.value() ? "true_value" : "false_value")
                    + ".");
            return new TextEvaluationResult(false, "", List.of(), issues);
        }
        return evaluateConditionalCharValue(value, scoped, depth + 1, issues);
    }

    static TextEvaluationResult evaluateConditionalCharValue(Object value,
            TextEvaluationScope scope,
            int depth,
            List<String> issues) { return ExpressionEngineDelegate.evaluateConditionalCharValue(value, scope, depth, issues); }

    static TextEvaluationResult evaluateTextListDetailed(Object config,
            TextEvaluationScope scope,
            int depth) { return ExpressionEngineDelegate.evaluateTextListDetailed(config, scope, depth); }

    static TextEvaluationResult evaluateStringTemplateDetailed(String template,
            TextEvaluationScope scope,
            int depth) { return ExpressionEngineDelegate.evaluateStringTemplateDetailed(template, scope, depth); }

    static TextPreparation prepareTextTemplate(String template, TextEvaluationScope scope, int depth) { return ExpressionEngineDelegate.prepareTextTemplate(template, scope, depth); }

    static TextEvaluationResult resolveTextVariable(String name,
            Object rawValue,
            TextEvaluationScope scope,
            int depth) { return ExpressionEngineDelegate.resolveTextVariable(name, rawValue, scope, depth); }

    static TextEvaluationResult resolveTextVariableValue(String name,
            Object rawValue,
            TextEvaluationScope scope,
            int depth) {
        if (rawValue == null) {
            return TextEvaluationResult.failure("文本表达式变量 '{" + name + "}' 为 null.", "", List.of());
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
        if (isNumericConfigType(type) || ExpressionEngineSupport.looksLikeNumericConfig(rawValue)) {
            NumericEvaluationResult result = evaluateRandomConfigDetailed(rawValue,
                    NumericEvaluationScope.of(scope.variables()),
                    depth + 1);
            if (result.success()) {
                return TextEvaluationResult.success(formatTextNumber(result.value()));
            }
            List<String> issues = new ArrayList<>();
            issues.add("文本表达式变量 '{" + name + "}' 的数值配置无效.");
            issues.addAll(result.issues());
            return TextEvaluationResult.failure(issues, "", List.of());
        }
        if (isBooleanConfigType(type)) {
            TextBooleanResult result = evaluateTextBooleanValue(
                    ConfigNodes.get(rawValue, "value"),
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

    static TextIntegerResult evaluateTextRollCount(Object config, TextEvaluationScope scope, int depth) { return ExpressionEngineDelegate.evaluateTextRollCount(config, scope, depth); }

    static TextBooleanResult evaluateTextAllowDuplicates(Object config, TextEvaluationScope scope, int depth) { return ExpressionEngineDelegate.evaluateTextAllowDuplicates(config, scope, depth); }

    static TextBooleanResult evaluateTextBooleanValue(Object value,
            TextEvaluationScope scope,
            int depth,
            boolean fallback) { return ExpressionEngineDelegate.evaluateTextBooleanValue(value, scope, depth, fallback); }

    static List<Object> randomTextLineCandidates(Object config) { return ExpressionEngineDelegate.randomTextLineCandidates(config); }

    static TextCandidates randomCharCandidates(Object config, TextEvaluationScope scope, int depth) { return ExpressionEngineDelegate.randomCharCandidates(config, scope, depth); }

    private static List<String> selectRandomChars(List<String> candidates,
            int requestedCount,
            boolean allowDuplicates) { return ExpressionEngineDelegate.selectRandomChars(candidates, requestedCount, allowDuplicates); }

    private static List<String> selectWeightedRandomChars(Object config,
            List<String> candidates,
            TextEvaluationScope scope,
            int depth,
            int requestedCount,
            boolean allowDuplicates,
            List<String> issues) {
        List<WeightedChar> weightedChars = weightedCharCandidates(config, candidates, scope, depth + 1, issues);
        if (!issues.isEmpty()) {
            return List.of();
        }
        int safeCount = requestedCount;
        if (!allowDuplicates) {
            safeCount = Math.min(safeCount, weightedChars.size());
        }
        if (safeCount <= 0) {
            return List.of();
        }
        List<String> selected = new ArrayList<>();
        if (allowDuplicates) {
            List<Randoms.Weighted<WeightedChar>> weightedItems = weightedItems(weightedChars);
            for (int index = 0; index < safeCount; index++) {
                WeightedChar selectedChar = Randoms.weightedRandom(weightedItems);
                if (selectedChar == null) {
                    break;
                }
                selected.add(selectedChar.value());
            }
            return selected;
        }
        List<WeightedChar> remaining = new ArrayList<>(weightedChars);
        for (int index = 0; index < safeCount && !remaining.isEmpty(); index++) {
            WeightedChar selectedChar = Randoms.weightedRandom(weightedItems(remaining));
            if (selectedChar == null) {
                break;
            }
            selected.add(selectedChar.value());
            remaining.remove(selectedChar);
        }
        return selected;
    }

    static List<WeightedChar> weightedCharCandidates(Object config,
            List<String> candidates,
            TextEvaluationScope scope,
            int depth,
            List<String> issues) { return ExpressionEngineDelegate2.weightedCharCandidates(config, candidates, scope, depth, issues); }

    static List<Randoms.Weighted<WeightedChar>> weightedItems(List<WeightedChar> candidates) { return ExpressionEngineDelegate2.weightedItems(candidates); }


    static TextEvaluationScope textScopeWithConfigVariables(Object config, TextEvaluationScope scope) { return ExpressionEngineDelegate2.textScopeWithConfigVariables(config, scope); }

    static Map<String, ?> mergedConfigVariables(Object config, Map<String, ?> variables) { return ExpressionEngineDelegate2.mergedConfigVariables(config, variables); }


    static boolean looksLikeTextConfig(Object config) { return ExpressionEngineDelegate2.looksLikeTextConfig(config); }


    private static String formatTextNumber(double value) { return ExpressionEngineDelegate2.formatTextNumber(value); }

    static NumericEvaluationResult evaluateRandomConfigDetailed(Object config,
            NumericEvaluationScope scope,
            int depth) { return ExpressionEngineDelegate2.evaluateRandomConfigDetailed(config, scope, depth); }

    static NumericEvaluationScope scopeWithConfigVariables(Object config, NumericEvaluationScope scope) { return ExpressionEngineDelegate2.scopeWithConfigVariables(config, scope); }

    static String normalizedConfigType(Object config) { return ExpressionEngineDelegate2.normalizedConfigType(config); }

    static boolean isStringConfigType(String type) { return ExpressionEngineDelegate2.isStringConfigType(type); }

    static boolean isBooleanConfigType(String type) { return ExpressionEngineDelegate2.isBooleanConfigType(type); }

    static boolean isNumericConfigType(String type) { return ExpressionEngineDelegate2.isNumericConfigType(type); }

    public record NumericEvaluationResult(boolean success, double value, List<String> issues) {

        public NumericEvaluationResult {
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
            if (!success && issues.isEmpty()) {
                issues = List.of("数值求值失败.");
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
                issues = List.of("文本求值失败.");
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

    record NumericPreparation(String expression, List<String> issues) {

        NumericPreparation {
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }
    }

    record TextPreparation(String value, List<String> issues) {

        TextPreparation {
            value = Texts.toStringSafe(value);
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }
    }

    record TextIntegerResult(int value, List<String> issues) {

        TextIntegerResult {
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }
    }

    record TextBooleanResult(boolean value, List<String> issues) {

        TextBooleanResult {
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }
    }

    record TextCandidates(List<String> values, List<String> issues) {

        TextCandidates {
            values = values == null || values.isEmpty() ? List.of() : List.copyOf(values);
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }
    }

    record WeightedChar(String value, double weight) {

        WeightedChar {
            value = Texts.toStringSafe(value);
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

        static final NumericEvaluationScope EMPTY_SCOPE = new NumericEvaluationScope(
                Map.of(), Map.of(), List.of());

        static NumericEvaluationScope of(Map<String, ?> variables) {
            if (variables == null || variables.isEmpty()) {
                return EMPTY_SCOPE;
            }
            return new NumericEvaluationScope(
                    variables,
                    new LinkedHashMap<>(),
                    new ArrayList<>()
            );
        }

        NumericEvaluationScope withVariables(Map<String, ?> variables) {
            if (variables == null || variables.isEmpty()) {
                return EMPTY_SCOPE;
            }
            return new NumericEvaluationScope(
                    variables,
                    new LinkedHashMap<>(),
                    resolvingVariables
            );
        }
    }
}
