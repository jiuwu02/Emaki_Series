package emaki.jiuwu.craft.corelib.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine.NumericEvaluationResult;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.math.Randoms;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * 处理随机数值配置的求值，包括 uniform、gaussian、skew_normal、triangle 等分布。
 */
final class RandomExpressionEvaluator {

    static final Pattern RANGE_PATTERN = Pattern.compile("^\\s*(.+)\\s*~\\s*(.+)\\s*$");
    private static final int DEFAULT_RANDOM_MAX_ATTEMPTS = 128;

    private RandomExpressionEvaluator() {
    }

    static NumericEvaluationResult evaluateRandomConfigDetailed(Object config,
            ExpressionEngine.NumericEvaluationScope scope,
            int depth,
            int maxNestedDepth) {
        if (depth > maxNestedDepth) {
            return NumericEvaluationResult.failure("Numeric config exceeded maximum nested depth "
                    + maxNestedDepth + ".");
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
            return evaluateNumericTextDetailed(text, scope, depth + 1, maxNestedDepth);
        }

        ExpressionEngine.NumericEvaluationScope scoped = ExpressionEngine.scopeWithConfigVariables(config, scope);
        String type = ExpressionEngine.normalizedConfigType(config);
        if (Texts.isBlank(type)) {
            Object value = ConfigNodes.get(config, "value");
            if (value != null) {
                return evaluateRandomConfigDetailed(value, scoped, depth + 1, maxNestedDepth);
            }
            if (ConfigNodes.get(config, "min") != null && ConfigNodes.get(config, "max") != null) {
                return evaluateUniformDetailed(config, scoped, depth + 1, maxNestedDepth);
            }
            Object expression = ConfigNodes.get(config, "expression");
            if (expression != null) {
                return ExpressionEngine.evaluateNumberDetailed(Texts.toStringSafe(expression), scoped, depth + 1);
            }
            return NumericEvaluationResult.failure("Numeric config does not declare a supported type or numeric fields.");
        }
        return switch (type) {
            case "constant", "const", "fixed" ->
                evaluateRequiredChildConfig(config, "value", scoped, depth + 1, "constant", maxNestedDepth);
            case "range" ->
                evaluateRangeDetailed(config, scoped, depth + 1, maxNestedDepth);
            case "uniform" ->
                evaluateUniformDetailed(config, scoped, depth + 1, maxNestedDepth);
            case "gaussian", "normal" ->
                evaluateGaussianDetailed(config, scoped, depth + 1, maxNestedDepth);
            case "skew_normal" ->
                evaluateSkewNormalDetailed(config, scoped, depth + 1, maxNestedDepth);
            case "triangle" ->
                evaluateTriangleDetailed(config, scoped, depth + 1, maxNestedDepth);
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

    static NumericEvaluationResult evaluateNumericTextDetailed(String text,
            ExpressionEngine.NumericEvaluationScope scope,
            int depth,
            int maxNestedDepth) {
        String prepared = Texts.trim(text);
        if (Texts.isBlank(prepared)) {
            return NumericEvaluationResult.failure("Numeric text is blank.");
        }
        Matcher matcher = RANGE_PATTERN.matcher(prepared);
        if (matcher.matches()) {
            NumericEvaluationResult min = ExpressionEngine.evaluateNumberDetailed(matcher.group(1), scope, depth + 1);
            NumericEvaluationResult max = ExpressionEngine.evaluateNumberDetailed(matcher.group(2), scope, depth + 1);
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
        if (ExpressionEngine.parseBooleanLiteral(prepared) != null) {
            return NumericEvaluationResult.failure("Boolean text cannot be used as numeric text: "
                    + ExpressionEngine.abbreviate(prepared));
        }
        return ExpressionEngine.evaluateNumberDetailed(prepared, scope, depth + 1);
    }

    private static NumericEvaluationResult evaluateRangeDetailed(Object config,
            ExpressionEngine.NumericEvaluationScope scope,
            int depth,
            int maxNestedDepth) {
        Object value = ConfigNodes.get(config, "value");
        if (value != null) {
            return evaluateRandomConfigDetailed(value, scope, depth + 1, maxNestedDepth);
        }
        return evaluateUniformDetailed(config, scope, depth + 1, maxNestedDepth);
    }

    private static NumericEvaluationResult evaluateUniformDetailed(Object config,
            ExpressionEngine.NumericEvaluationScope scope,
            int depth,
            int maxNestedDepth) {
        NumericEvaluationResult min = evaluateRequiredChildConfig(config, "min", scope, depth + 1, "uniform", maxNestedDepth);
        NumericEvaluationResult max = evaluateRequiredChildConfig(config, "max", scope, depth + 1, "uniform", maxNestedDepth);
        List<String> issues = new ArrayList<>();
        issues.addAll(min.issues());
        issues.addAll(max.issues());
        if (!min.success() || !max.success()) {
            return NumericEvaluationResult.failure(issues);
        }
        return NumericEvaluationResult.success(Randoms.uniform(min.value(), max.value()));
    }

    private static NumericEvaluationResult evaluateGaussianDetailed(Object config,
            ExpressionEngine.NumericEvaluationScope scope,
            int depth,
            int maxNestedDepth) {
        DistributionParamsResult paramsResult = resolveDistributionParamsDetailed(config, scope, depth + 1, maxNestedDepth);
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
            ExpressionEngine.NumericEvaluationScope scope,
            int depth,
            int maxNestedDepth) {
        DistributionParamsResult paramsResult = resolveDistributionParamsDetailed(config, scope, depth + 1, maxNestedDepth);
        OptionalNumericResult skewness = optionalNumberDetailed(ConfigNodes.get(config, "skewness"), scope, depth + 1, maxNestedDepth);
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
            ExpressionEngine.NumericEvaluationScope scope,
            int depth,
            int maxNestedDepth) {
        OptionalNumericResult mode = optionalNumberDetailed(ConfigNodes.get(config, "mode"), scope, depth + 1, maxNestedDepth);
        OptionalNumericResult deviation = optionalNumberDetailed(ConfigNodes.get(config, "deviation"), scope, depth + 1, maxNestedDepth);
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
            ExpressionEngine.NumericEvaluationScope scope,
            int depth) {
        Object expression = ConfigNodes.get(config, "expression");
        if (expression == null) {
            expression = ConfigNodes.get(config, "formula");
        }
        if (expression == null) {
            return NumericEvaluationResult.failure("Numeric expression config is missing 'expression'.");
        }
        return ExpressionEngine.evaluateNumberDetailed(Texts.toStringSafe(expression), scope, depth + 1);
    }

    static NumericEvaluationResult evaluateRequiredChildConfig(Object config,
            String key,
            ExpressionEngine.NumericEvaluationScope scope,
            int depth,
            String type,
            int maxNestedDepth) {
        Object value = ConfigNodes.get(config, key);
        if (value == null) {
            return NumericEvaluationResult.failure("Numeric config type '" + type
                    + "' is missing required field '" + key + "'.");
        }
        NumericEvaluationResult result = evaluateRandomConfigDetailed(value, scope, depth + 1, maxNestedDepth);
        if (result.success()) {
            return result;
        }
        List<String> issues = new ArrayList<>();
        issues.add("Numeric config field '" + key + "' for type '" + type + "' is invalid.");
        issues.addAll(result.issues());
        return NumericEvaluationResult.failure(issues);
    }

    static OptionalNumericResult optionalNumberDetailed(Object value,
            ExpressionEngine.NumericEvaluationScope scope,
            int depth,
            int maxNestedDepth) {
        if (value == null) {
            return new OptionalNumericResult(null, List.of());
        }
        NumericEvaluationResult result = evaluateRandomConfigDetailed(value, scope, depth + 1, maxNestedDepth);
        return result.success()
                ? new OptionalNumericResult(result.value(), List.of())
                : new OptionalNumericResult(null, result.issues());
    }

    private static DistributionParamsResult resolveDistributionParamsDetailed(Object config,
            ExpressionEngine.NumericEvaluationScope scope,
            int depth,
            int maxNestedDepth) {
        OptionalNumericResult min = optionalNumberDetailed(ConfigNodes.get(config, "min"), scope, depth + 1, maxNestedDepth);
        OptionalNumericResult max = optionalNumberDetailed(ConfigNodes.get(config, "max"), scope, depth + 1, maxNestedDepth);
        OptionalNumericResult mean = optionalNumberDetailed(ConfigNodes.get(config, "mean"), scope, depth + 1, maxNestedDepth);
        Object stdDevValue = ConfigNodes.get(config, "std_dev");
        if (stdDevValue == null) {
            stdDevValue = ConfigNodes.get(config, "std-dev");
        }
        OptionalNumericResult stdDev = optionalNumberDetailed(stdDevValue, scope, depth + 1, maxNestedDepth);
        OptionalNumericResult maxAttempts = optionalNumberDetailed(ConfigNodes.get(config, "max_attempts"),
                scope, depth + 1, maxNestedDepth);
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

    record DistributionParams(Double min, Double max, double mean, double stdDev, int maxAttempts) {

    }

    record DistributionParamsResult(DistributionParams params, List<String> issues) {

        DistributionParamsResult {
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }
    }

    record OptionalNumericResult(Double value, List<String> issues) {

        OptionalNumericResult {
            issues = issues == null || issues.isEmpty() ? List.of() : List.copyOf(issues);
        }
    }
}
