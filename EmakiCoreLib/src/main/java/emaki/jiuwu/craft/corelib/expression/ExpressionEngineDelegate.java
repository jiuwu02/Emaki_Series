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

final class ExpressionEngineDelegate {

    private final ExpressionEngine ownerRef;

    ExpressionEngineDelegate(ExpressionEngine ownerRef) {
        this.ownerRef = ownerRef;
    }

    static boolean isBuiltinFunctionName(String name) {
        if (Texts.isBlank(name)) {
            return false;
        }
        String lowered = Texts.lower(name);
        for (Function function : ExpressionEngine.CUSTOM_FUNCTIONS) {
            if (lowered.equals(function.getName())) {
                return true;
            }
        }
        return false;
    }

    static Function[] mergedFunctions() {
        List<Function> dynamic = ExpressionEngine.dynamicFunctionSnapshot();
        if (dynamic.isEmpty()) {
            return ExpressionEngine.CUSTOM_FUNCTIONS;
        }
        List<Function> merged = new ArrayList<>(List.of(ExpressionEngine.CUSTOM_FUNCTIONS));
        merged.addAll(dynamic);
        return merged.toArray(new Function[0]);
    }

    static double evaluate(String expression) {
        return ExpressionEngine.evaluate(expression, Map.of());
    }

    static double evaluate(String expression, Map<String, ?> variables) {
        Double value = ExpressionEngine.evaluateNumberOrNull(expression, variables);
        return value == null ? 0D : value;
    }

    static Map<String, Double> resolveVariables(Map<String, Object> rawValues, Map<String, ?> context) {
        Map<String, Double> resolved = new LinkedHashMap<>();
        if (rawValues == null || rawValues.isEmpty()) {
            return resolved;
        }
        Map<String, Object> evalContext = new LinkedHashMap<>();
        if (context != null) {
            evalContext.putAll(context);
        }
        for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
            String key = entry.getKey();
            Object raw = entry.getValue();
            double value;
            if (raw instanceof Number number) {
                value = number.doubleValue();
            } else {
                String expression = raw == null ? "0" : String.valueOf(raw).trim();
                value = ExpressionEngine.evaluate(expression, evalContext);
            }
            resolved.put(key, value);
            evalContext.put(key, value);
        }
        return resolved;
    }

    static Map<String, Object> resolveMixedVariables(Map<String, Object> rawValues, Map<String, ?> context) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        if (rawValues == null || rawValues.isEmpty()) {
            return resolved;
        }
        Map<String, Object> evalContext = new LinkedHashMap<>();
        if (context != null) {
            evalContext.putAll(context);
        }
        for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
            String key = entry.getKey();
            if (Texts.isBlank(key)) {
                continue;
            }
            Object value = ExpressionEngine.resolveMixedVariableValue(entry.getValue(), evalContext);
            resolved.put(key, value);
            evalContext.put(key, value);
        }
        return resolved;
    }

    static ExpressionEngine.NumericEvaluationResult evaluateNumericDetailed(String expression) {
        return ExpressionEngine.evaluateNumericDetailed(expression, Map.of());
    }

    static ExpressionEngine.NumericEvaluationResult evaluateNumericDetailed(String expression, Map<String, ?> variables) {
        return ExpressionEngine.evaluateNumberDetailed(expression, ExpressionEngine.NumericEvaluationScope.of(variables), 0);
    }

    static String evaluateString(String expression) {
        return ExpressionEngine.evaluateString(expression, Map.of());
    }

    static String evaluateString(String expression, Map<String, ?> variables) {
        if (expression == null) {
            return "";
        }
        if (!ExpressionEngineSupport.validateExpressionLength(expression) || ExpressionEngineSupport.containsDangerousChars(expression)) {
            return "";
        }
        ExpressionEngine.TextEvaluationResult result = ExpressionEngine.evaluateStringTemplateDetailed(expression, ExpressionEngine.TextEvaluationScope.of(variables), 0);
        return result.value();
    }

    static String evaluateStringConfig(Object config) {
        return ExpressionEngine.evaluateStringConfig(config, Map.of());
    }

    static String evaluateStringConfig(Object config, Map<String, ?> variables) {
        return ExpressionEngine.evaluateStringConfigDetailed(config, variables).value();
    }

    static List<String> evaluateStringLinesConfig(Object config) {
        return ExpressionEngine.evaluateStringLinesConfig(config, Map.of());
    }

    static List<String> evaluateStringLinesConfig(Object config, Map<String, ?> variables) {
        return ExpressionEngine.evaluateStringConfigDetailed(config, variables).lines();
    }

    static Boolean evaluateBoolean(String expression) {
        return ExpressionEngine.evaluateBoolean(expression, Map.of());
    }

    static Boolean evaluateBoolean(String expression, Map<String, ?> variables) {
        String prepared = ExpressionEngine.prepareExpression(expression, variables);
        if (prepared == null) {
            return null;
        }
        return BooleanExpressionEvaluator.evaluatePreparedBoolean(prepared);
    }

    static boolean evaluateBoolean(String expression, Map<String, ?> variables, boolean fallback) {
        Boolean value = ExpressionEngine.evaluateBoolean(expression, variables);
        return value == null ? fallback : value;
    }

    static Object resolveMixedVariableValue(Object raw, Map<String, ?> variables) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof Number || raw instanceof Boolean) {
            return raw;
        }
        ExpressionEngine.TextEvaluationResult text = ExpressionEngine.evaluateStringConfigDetailed(raw, variables);
        if (text.success()) {
            return ExpressionEngine.coerceResolvedTextValue(raw, text.value());
        }
        ExpressionEngine.NumericEvaluationResult numeric = ExpressionEngine.evaluateRandomConfigDetailed(raw, variables);
        if (numeric.success()) {
            return numeric.value();
        }
        return ExpressionEngine.coerceResolvedTextValue(raw, text.value());
    }

    static Object coerceResolvedTextValue(Object raw, String value) {
        String safeValue = Texts.toStringSafe(value);
        if (raw instanceof Map<?, ?> && ExpressionEngine.looksLikeTextConfig(raw)) {
            return safeValue;
        }
        if (raw instanceof Iterable<?>) {
            return safeValue;
        }
        Double numeric = Numbers.tryParseDouble(safeValue, null);
        return numeric == null ? safeValue : numeric;
    }

    static Double evaluateNumberOrNull(String expression, Map<String, ?> variables) {
        ExpressionEngine.NumericEvaluationResult result = ExpressionEngine.evaluateNumericDetailed(expression, variables);
        return result.success() ? result.value() : null;
    }

    static double evaluateRandomConfig(Object config) {
        return ExpressionEngine.evaluateRandomConfig(config, Map.of());
    }

    static double evaluateRandomConfig(Object config, Map<String, ?> variables) {
        ExpressionEngine.NumericEvaluationResult result = ExpressionEngine.evaluateRandomConfigDetailed(config, variables);
        return result.success() ? result.value() : 0D;
    }

    static ExpressionEngine.NumericEvaluationResult evaluateRandomConfigDetailed(Object config) {
        return ExpressionEngine.evaluateRandomConfigDetailed(config, Map.of());
    }

    static ExpressionEngine.NumericEvaluationResult evaluateRandomConfigDetailed(Object config, Map<String, ?> variables) {
        return ExpressionEngine.evaluateRandomConfigDetailed(config, ExpressionEngine.NumericEvaluationScope.of(variables), 0);
    }

    static String replaceVariables(String expression, Map<String, ?> variables) {
        if (expression == null) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return expression;
        }
        Matcher matcher = ExpressionEngine.VARIABLE_PATTERN.matcher(expression);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(Texts.toStringSafe(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    static String evaluateExpressionBlock(String innerExpression, Matcher matcher, Map<String, ?> variables) {
        String prepared = Texts.toStringSafe(innerExpression);
        if (matcher != null) {
            for (int group = 1; group <= matcher.groupCount(); group++) {
                String groupValue = matcher.group(group);
                if (groupValue != null) {
                    prepared = prepared.replace("$" + group, groupValue);
                }
            }
        }
        prepared = ExpressionEngine.replaceVariables(prepared, variables);
        if (!ExpressionEngine.isPureNumericExpression(prepared)) {
            Boolean booleanResult = ExpressionEngine.evaluateBoolean(prepared, Map.of());
            return booleanResult == null ? ExpressionEngine.evaluateString(prepared, Map.of()) : Boolean.toString(booleanResult);
        }
        try {
            double result = ExpressionEngine.evaluate(prepared);
            if (Math.abs(result - Math.rint(result)) <= ExpressionEngine.INTEGER_ROUNDING_EPSILON) {
                return Long.toString(Math.round(result));
            }
            return Numbers.formatNumber(result, "0.##");
        } catch (Exception e) {
            return prepared;
        }
    }

    static void clearThreadLocalCache() {
        ExpressionCache.clearThreadLocal();
    }

    static void clearGlobalCache() {
        ExpressionCache.clearGlobal();
    }

    static String prepareExpression(String expression, Map<String, ?> variables) {
        if (Texts.isBlank(expression)) {
            return null;
        }
        if (!ExpressionEngineSupport.validateExpressionLength(expression) || ExpressionEngineSupport.containsDangerousChars(expression)) {
            return null;
        }
        String prepared = ExpressionEngine.replaceVariables(expression, variables).trim();
        if (Texts.isBlank(prepared) || !ExpressionEngineSupport.validateExpressionLength(prepared) || ExpressionEngineSupport.containsDangerousChars(prepared)) {
            return null;
        }
        return prepared;
    }

    static boolean isPureNumericExpression(String expression) {
        return ExpressionRules.isPureNumericExpression(expression);
    }

    static String abbreviate(Object value) {
        String text = Texts.toStringSafe(value).replace('\n', ' ').replace('\r', ' ').trim();
        return text.length() <= 96 ? text : text.substring(0, 93) + "...";
    }

    static Boolean parseBooleanLiteral(String raw) {
        return BooleanExpressionEvaluator.parseBooleanLiteral(raw);
    }

    static ExpressionEngine.NumericPreparation prepareNumericExpression(String expression, ExpressionEngine.NumericEvaluationScope scope, int depth) {
        if (scope.variables().isEmpty()) {
            return new ExpressionEngine.NumericPreparation(expression, List.of());
        }
        Matcher matcher = ExpressionEngine.VARIABLE_PATTERN.matcher(expression);
        StringBuilder buffer = new StringBuilder();
        List<String> issues = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!scope.variables().containsKey(name)) {
                issues.add("数值表达式变量 '{" + name + "}' 缺失.");
                matcher.appendReplacement(buffer, "0");
                continue;
            }
            ExpressionEngine.NumericEvaluationResult variableResult = ExpressionEngine.resolveNumericVariable(name, scope.variables().get(name),
                    scope, depth + 1);
            if (!variableResult.success()) {
                issues.addAll(variableResult.issues());
                matcher.appendReplacement(buffer, "0");
                continue;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(Numbers.toPlainString(variableResult.value())));
        }
        matcher.appendTail(buffer);
        return new ExpressionEngine.NumericPreparation(buffer.toString(), issues);
    }

    static ExpressionEngine.NumericEvaluationResult resolveNumericVariable(String name, Object rawValue, ExpressionEngine.NumericEvaluationScope scope, int depth) {
        if (depth > ExpressionRules.MAX_NESTED_DEPTH) {
            return ExpressionEngine.NumericEvaluationResult.failure("数值表达式变量 '{" + name + "}' 超过最大嵌套深度 " + ExpressionRules.MAX_NESTED_DEPTH + ".");
        }
        Double cached = scope.resolvedVariables().get(name);
        if (cached != null) {
            return ExpressionEngine.NumericEvaluationResult.success(cached);
        }
        if (scope.resolvingVariables().contains(name)) {
            return ExpressionEngine.NumericEvaluationResult.failure("数值表达式变量 '{" + name
                    + "}' 引用了自身.");
        }
        scope.resolvingVariables().add(name);
        try {
            ExpressionEngine.NumericEvaluationResult result = ExpressionEngine.resolveNumericVariableValue(name, rawValue, scope, depth + 1);
            if (result.success()) {
                scope.resolvedVariables().put(name, result.value());
            }
            return result;
        } finally {
            scope.resolvingVariables().remove(scope.resolvingVariables().size() - 1);
        }
    }

    static ExpressionEngine.NumericEvaluationResult resolveNumericVariableValue(String name, Object rawValue, ExpressionEngine.NumericEvaluationScope scope, int depth) {
        if (rawValue == null) {
            return ExpressionEngine.NumericEvaluationResult.failure("数值表达式变量 '{" + name + "}' 为 null.");
        }
        if (rawValue instanceof Number number) {
            return ExpressionEngine.NumericEvaluationResult.success(number.doubleValue());
        }
        if (rawValue instanceof Boolean) {
            return ExpressionEngine.NumericEvaluationResult.failure("数值表达式变量 '{" + name + "}' 是布尔值, 不能用于数值表达式.");
        }
        if (rawValue instanceof String text) {
            ExpressionEngine.NumericEvaluationResult result = RandomExpressionEvaluator.evaluateNumericTextDetailed(text, scope, depth + 1, ExpressionRules.MAX_NESTED_DEPTH);
            if (result.success()) {
                return result;
            }
            List<String> issues = new ArrayList<>();
            issues.add("数值表达式变量 '{" + name + "}' 必须解析为数字, 但得到字符串: "
                    + ExpressionEngine.abbreviate(text));
            issues.addAll(result.issues());
            return ExpressionEngine.NumericEvaluationResult.failure(issues);
        }
        String type = ExpressionEngine.normalizedConfigType(rawValue);
        if (ExpressionEngine.isStringConfigType(type) || ExpressionEngine.isBooleanConfigType(type)) {
            return ExpressionEngine.NumericEvaluationResult.failure("数值表达式变量 '{" + name + "}' 使用了 "
                    + type + " 配置, 不能用于数值表达式.");
        }
        ExpressionEngine.NumericEvaluationResult result = ExpressionEngine.evaluateRandomConfigDetailed(rawValue, scope, depth + 1);
        if (result.success()) {
            return result;
        }
        List<String> issues = new ArrayList<>();
        issues.add("数值表达式变量 '{" + name + "}' 必须解析为数值配置.");
        issues.addAll(result.issues());
        return ExpressionEngine.NumericEvaluationResult.failure(issues);
    }

    static ExpressionEngine.TextEvaluationResult evaluateRandomTextLinesDetailed(Object config, ExpressionEngine.TextEvaluationScope scope, int depth) {
        if (depth > ExpressionRules.MAX_NESTED_DEPTH) {
            return ExpressionEngine.TextEvaluationResult.failure("随机文本配置超过最大嵌套深度 "
                    + ExpressionRules.MAX_NESTED_DEPTH + ".");
        }
        ExpressionEngine.TextEvaluationScope scoped = ExpressionEngine.textScopeWithConfigVariables(config, scope);
        List<Object> candidates = ExpressionEngine.randomTextLineCandidates(config);
        if (candidates.isEmpty()) {
            return ExpressionEngine.TextEvaluationResult.failure("随机文本配置缺少 'lines' 或 'values'.");
        }

        ExpressionEngine.TextIntegerResult count = ExpressionEngine.evaluateTextRollCount(config, scoped, depth + 1);
        ExpressionEngine.TextBooleanResult allowDuplicates = ExpressionEngine.evaluateTextAllowDuplicates(config, scoped, depth + 1);
        List<String> issues = new ArrayList<>();
        issues.addAll(count.issues());
        issues.addAll(allowDuplicates.issues());
        int requestedCount = Math.max(0, count.value());
        if (!allowDuplicates.value()) {
            requestedCount = Math.min(requestedCount, candidates.size());
        }
        if (requestedCount <= 0) {
            return new ExpressionEngine.TextEvaluationResult(issues.isEmpty(), "", List.of(), issues);
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
            ExpressionEngine.TextEvaluationResult lineResult = ExpressionEngine.evaluateStringConfigDetailed(rawLine, scoped.fresh(), depth + 1);
            issues.addAll(lineResult.issues());
            if (lineResult.lines().isEmpty()) {
                lines.add(lineResult.value());
                continue;
            }
            lines.addAll(lineResult.lines());
        }
        String separator = Texts.toStringSafe(ConfigNodes.get(config, "separator"));
        if (separator.isEmpty()) {
            separator = "\n";
        }
        return new ExpressionEngine.TextEvaluationResult(issues.isEmpty(), String.join(separator, lines), lines, issues);
    }

    static ExpressionEngine.TextEvaluationResult evaluateConditionalCharValue(Object value, ExpressionEngine.TextEvaluationScope scope, int depth, List<String> issues) {
        ExpressionEngine.TextEvaluationResult result = ExpressionEngine.evaluateStringConfigDetailed(value, scope.fresh(), depth + 1);
        List<String> allIssues = new ArrayList<>(issues == null ? List.of() : issues);
        allIssues.addAll(result.issues());
        return new ExpressionEngine.TextEvaluationResult(allIssues.isEmpty(), result.value(), result.lines(), allIssues);
    }

    static ExpressionEngine.TextEvaluationResult evaluateTextListDetailed(Object config, ExpressionEngine.TextEvaluationScope scope, int depth) {
        if (depth > ExpressionRules.MAX_NESTED_DEPTH) {
            return ExpressionEngine.TextEvaluationResult.failure("文本列表超过最大嵌套深度 " + ExpressionRules.MAX_NESTED_DEPTH + ".");
        }
        List<String> lines = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(config)) {
            ExpressionEngine.TextEvaluationResult result = ExpressionEngine.evaluateStringConfigDetailed(entry, scope.fresh(), depth + 1);
            issues.addAll(result.issues());
            if (result.lines().isEmpty()) {
                lines.add(result.value());
                continue;
            }
            lines.addAll(result.lines());
        }
        return new ExpressionEngine.TextEvaluationResult(issues.isEmpty(), String.join("\n", lines), lines, issues);
    }

    static ExpressionEngine.TextEvaluationResult evaluateStringTemplateDetailed(String template, ExpressionEngine.TextEvaluationScope scope, int depth) {
        if (depth > ExpressionRules.MAX_NESTED_DEPTH) {
            return ExpressionEngine.TextEvaluationResult.failure("文本表达式超过最大嵌套深度 "
                    + ExpressionRules.MAX_NESTED_DEPTH + ".");
        }
        if (template == null) {
            return ExpressionEngine.TextEvaluationResult.success("");
        }
        if (!ExpressionEngineSupport.validateExpressionLength(template)) {
            return ExpressionEngine.TextEvaluationResult.failure("文本表达式长度超过 "
                    + ExpressionRules.MAX_EXPRESSION_LENGTH + " 个字符: " + ExpressionEngine.abbreviate(template));
        }
        if (ExpressionEngineSupport.containsDangerousChars(template)) {
            return ExpressionEngine.TextEvaluationResult.failure("文本表达式包含不支持的字符: "
                    + ExpressionEngine.abbreviate(template));
        }
        ExpressionEngine.TextPreparation preparation = ExpressionEngine.prepareTextTemplate(template, scope, depth + 1);
        String prepared = BooleanExpressionEvaluator.unquote(preparation.value());
        if (!ExpressionEngineSupport.validateExpressionLength(prepared)) {
            List<String> issues = new ArrayList<>(preparation.issues());
            issues.add("预处理后的文本表达式长度超过 "
                    + ExpressionRules.MAX_EXPRESSION_LENGTH + " 个字符: " + ExpressionEngine.abbreviate(prepared));
            return ExpressionEngine.TextEvaluationResult.failure(issues, "", List.of());
        }
        if (ExpressionEngineSupport.containsDangerousChars(prepared)) {
            List<String> issues = new ArrayList<>(preparation.issues());
            issues.add("预处理后的文本表达式包含不支持的字符: " + ExpressionEngine.abbreviate(prepared));
            return ExpressionEngine.TextEvaluationResult.failure(issues, "", List.of());
        }
        return new ExpressionEngine.TextEvaluationResult(preparation.issues().isEmpty(), prepared, List.of(prepared),
                preparation.issues());
    }

    static ExpressionEngine.TextPreparation prepareTextTemplate(String template, ExpressionEngine.TextEvaluationScope scope, int depth) {
        Matcher matcher = ExpressionEngine.VARIABLE_PATTERN.matcher(template);
        StringBuilder buffer = new StringBuilder();
        List<String> issues = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!scope.variables().containsKey(name)) {
                issues.add("文本表达式变量 '{" + name + "}' 缺失.");
                matcher.appendReplacement(buffer, "");
                continue;
            }
            ExpressionEngine.TextEvaluationResult variable = ExpressionEngine.resolveTextVariable(name, scope.variables().get(name), scope, depth + 1);
            issues.addAll(variable.issues());
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(variable.value()));
        }
        matcher.appendTail(buffer);
        return new ExpressionEngine.TextPreparation(buffer.toString(), issues);
    }

    static ExpressionEngine.TextEvaluationResult resolveTextVariable(String name, Object rawValue, ExpressionEngine.TextEvaluationScope scope, int depth) {
        if (depth > ExpressionRules.MAX_NESTED_DEPTH) {
            return ExpressionEngine.TextEvaluationResult.failure("文本表达式变量 '{" + name + "}' 超过最大嵌套深度 " + ExpressionRules.MAX_NESTED_DEPTH + ".");
        }
        if (scope.resolvingVariables().contains(name)) {
            return ExpressionEngine.TextEvaluationResult.failure("文本表达式变量 '{" + name + "}' 引用了自身.");
        }
        scope.resolvingVariables().add(name);
        try {
            return ExpressionEngine.resolveTextVariableValue(name, rawValue, scope, depth + 1);
        } finally {
            scope.resolvingVariables().remove(scope.resolvingVariables().size() - 1);
        }
    }

    static ExpressionEngine.TextIntegerResult evaluateTextRollCount(Object config, ExpressionEngine.TextEvaluationScope scope, int depth) {
        Object value = ConfigNodes.get(config, "count");
        if (value == null) {
            return new ExpressionEngine.TextIntegerResult(1, List.of());
        }
        ExpressionEngine.NumericEvaluationResult result = ExpressionEngine.evaluateRandomConfigDetailed(value,
                ExpressionEngine.NumericEvaluationScope.of(scope.variables()),
                depth + 1);
        if (result.success()) {
            return new ExpressionEngine.TextIntegerResult(Math.max(0, (int) Math.round(result.value())), List.of());
        }
        List<String> issues = new ArrayList<>();
        issues.add("随机文本掷取次数必须解析为数字.");
        issues.addAll(result.issues());
        return new ExpressionEngine.TextIntegerResult(0, issues);
    }

    static ExpressionEngine.TextBooleanResult evaluateTextAllowDuplicates(Object config, ExpressionEngine.TextEvaluationScope scope, int depth) {
        Object value = ConfigNodes.get(config, "allow_duplicates");
        if (value == null) {
            return new ExpressionEngine.TextBooleanResult(false, List.of());
        }
        return ExpressionEngine.evaluateTextBooleanValue(value, scope, depth + 1, false);
    }

    static ExpressionEngine.TextBooleanResult evaluateTextBooleanValue(Object value, ExpressionEngine.TextEvaluationScope scope, int depth, boolean fallback) {
        if (value == null) {
            return new ExpressionEngine.TextBooleanResult(fallback, List.of());
        }
        if (value instanceof Boolean bool) {
            return new ExpressionEngine.TextBooleanResult(bool, List.of());
        }
        if (value instanceof Number number) {
            return new ExpressionEngine.TextBooleanResult(number.doubleValue() > 0D, List.of());
        }
        Object expression = value;
        if (!(value instanceof String)) {
            ExpressionEngine.TextEvaluationScope scoped = ExpressionEngine.textScopeWithConfigVariables(value, scope);
            expression = ConfigNodes.get(value, "value");
            if (expression == null) {
                return new ExpressionEngine.TextBooleanResult(fallback, List.of("布尔文本配置缺少 value 或 expression."));
            }
            scope = scoped;
        }
        Boolean result = ExpressionEngine.evaluateBoolean(Texts.toStringSafe(expression), scope.variables());
        if (result == null) {
            return new ExpressionEngine.TextBooleanResult(fallback, List.of("布尔文本配置无法求值: "
                    + ExpressionEngine.abbreviate(expression)));
        }
        return new ExpressionEngine.TextBooleanResult(result, List.of());
    }

    static List<Object> randomTextLineCandidates(Object config) {
        Object value = ConfigNodes.get(config, "lines");
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

    static ExpressionEngine.TextCandidates randomCharCandidates(Object config, ExpressionEngine.TextEvaluationScope scope, int depth) {
        Object raw = ConfigNodes.get(config, "chars");
        if (raw == null) {
            raw = ExpressionEngine.DEFAULT_RANDOM_CHARS;
        }
        List<String> result = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        if (raw instanceof String text) {
            ExpressionEngine.TextEvaluationResult evaluated = ExpressionEngine.evaluateStringConfigDetailed(text, scope.fresh(), depth + 1);
            issues.addAll(evaluated.issues());
            evaluated.value().codePoints()
                    .mapToObj(codePoint -> new String(Character.toChars(codePoint)))
                    .filter(Texts::isNotBlank)
                    .forEach(result::add);
            return new ExpressionEngine.TextCandidates(result, issues);
        }
        for (Object entry : ConfigNodes.asObjectList(raw)) {
            if (entry == null) {
                continue;
            }
            ExpressionEngine.TextEvaluationResult evaluated = ExpressionEngine.evaluateStringConfigDetailed(entry, scope.fresh(), depth + 1);
            issues.addAll(evaluated.issues());
            if (Texts.isNotBlank(evaluated.value())) {
                result.add(evaluated.value());
            }
        }
        return new ExpressionEngine.TextCandidates(result, issues);
    }

    static List<String> selectRandomChars(List<String> candidates, int requestedCount, boolean allowDuplicates) {
        int safeCount = requestedCount;
        if (!allowDuplicates) {
            safeCount = Math.min(safeCount, candidates.size());
        }
        if (safeCount <= 0) {
            return List.of();
        }
        List<String> selected = new ArrayList<>();
        if (allowDuplicates) {
            for (int index = 0; index < safeCount; index++) {
                selected.add(candidates.get(Randoms.randomInt(0, candidates.size() - 1)));
            }
            return selected;
        }
        selected.addAll(Randoms.shuffle(candidates).subList(0, safeCount));
        return selected;
    }

}
