package emaki.jiuwu.craft.attribute.formula;

import java.util.Map;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class AttributeFormulaEvaluator {

    private AttributeFormulaEvaluator() {
    }

    public static double evaluate(String expression,
            Map<String, Object> variables,
            double defaultValue,
            Double minResult,
            Double maxResult) {
        double result = Texts.isBlank(expression)
                ? defaultValue
                : ExpressionEngine.evaluate(expression, variables);
        return clampResult(result, minResult, maxResult);
    }

    public static double evaluate(String expression, Map<String, Object> variables, double defaultValue) {
        return evaluate(expression, variables, defaultValue, null, null);
    }

    public static double clampResult(double value, Double min, Double max) {
        double result = value;
        if (min != null) {
            result = Math.max(result, min);
        }
        if (max != null) {
            result = Math.min(result, max);
        }
        return Math.max(0D, result);
    }
}
