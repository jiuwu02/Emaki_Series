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

final class ExpressionEngineDelegate2 {

    private final ExpressionEngine ownerRef;

    ExpressionEngineDelegate2(ExpressionEngine ownerRef) {
        this.ownerRef = ownerRef;
    }

    static List<ExpressionEngine.WeightedChar> weightedCharCandidates(Object config, List<String> candidates, ExpressionEngine.TextEvaluationScope scope, int depth, List<String> issues) {
        Object rawWeights = ConfigNodes.get(config, "weights");
        if (rawWeights == null) {
            issues.add("加权随机字符配置缺少 'weights'。");
            return List.of();
        }
        List<Object> weights = ConfigNodes.asObjectList(rawWeights);
        if (weights.size() != candidates.size()) {
            issues.add("加权随机字符配置要求权重数量与字符数量一致。");
            return List.of();
        }
        List<ExpressionEngine.WeightedChar> result = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            ExpressionEngine.NumericEvaluationResult weight = ExpressionEngine.evaluateRandomConfigDetailed(weights.get(index),
                    ExpressionEngine.NumericEvaluationScope.of(scope.variables()),
                    depth + 1);
            if (!weight.success()) {
                issues.add("索引 " + index + " 处的加权随机字符权重无效。");
                issues.addAll(weight.issues());
                continue;
            }
            if (weight.value() > 0D) {
                result.add(new ExpressionEngine.WeightedChar(candidates.get(index), weight.value()));
            }
        }
        if (result.isEmpty()) {
            issues.add("加权随机字符配置没有正权重。");
        }
        return result;
    }

    static List<Randoms.Weighted<ExpressionEngine.WeightedChar>> weightedItems(List<ExpressionEngine.WeightedChar> candidates) {
        List<Randoms.Weighted<ExpressionEngine.WeightedChar>> result = new ArrayList<>();
        for (ExpressionEngine.WeightedChar candidate : candidates) {
            result.add(new Randoms.Weighted<>(candidate, candidate.weight()));
        }
        return result;
    }

    static ExpressionEngine.TextEvaluationScope textScopeWithConfigVariables(Object config, ExpressionEngine.TextEvaluationScope scope) {
        return scope.withVariables(ExpressionEngine.mergedConfigVariables(config, scope.variables()));
    }

    static Map<String, ?> mergedConfigVariables(Object config, Map<String, ?> variables) {
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

    static boolean looksLikeTextConfig(Object config) {
        String type = ExpressionEngine.normalizedConfigType(config);
        if (ExpressionEngine.isStringConfigType(type)
                || ExpressionEngineSupport.isRandomTextConfigType(type)
                || ExpressionEngineSupport.isRandomCharConfigType(type)
                || ExpressionEngineSupport.isWeightedRandomCharConfigType(type)
                || ExpressionEngineSupport.isConditionalCharConfigType(type)
                || ExpressionEngine.isBooleanConfigType(type)) {
            return true;
        }
        return Texts.isBlank(type) && (ExpressionEngineSupport.hasRandomTextLines(config)
                || ConfigNodes.get(config, "text") != null);
    }

    static String formatTextNumber(double value) {
        if (Math.abs(value - Math.rint(value)) <= ExpressionEngine.INTEGER_ROUNDING_EPSILON) {
            return Long.toString(Math.round(value));
        }
        return Numbers.formatNumber(value, "0.##");
    }

    static ExpressionEngine.NumericEvaluationResult evaluateRandomConfigDetailed(Object config, ExpressionEngine.NumericEvaluationScope scope, int depth) {
        return RandomExpressionEvaluator.evaluateRandomConfigDetailed(config, scope, depth, ExpressionRules.MAX_NESTED_DEPTH);
    }

    static ExpressionEngine.NumericEvaluationScope scopeWithConfigVariables(Object config, ExpressionEngine.NumericEvaluationScope scope) {
        Map<String, ?> merged = ExpressionEngine.mergedConfigVariables(config, scope.variables());
        return merged == scope.variables() ? scope : scope.withVariables(merged);
    }

    static String normalizedConfigType(Object config) {
        return Texts.lower(ConfigNodes.string(config, "type", "")).replace('-', '_').trim();
    }

    static boolean isStringConfigType(String type) {
        return "string".equals(type);
    }

    static boolean isBooleanConfigType(String type) {
        return "boolean".equals(type);
    }

    static boolean isNumericConfigType(String type) {
        return switch (type) {
            case "constant", "range", "uniform", "gaussian",
                    "skew_normal", "triangle", "expression" ->
                true;
            default ->
                false;
        };
    }

}
