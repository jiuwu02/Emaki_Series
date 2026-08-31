package emaki.jiuwu.craft.corelib.progression;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class FormulaProgression<T> implements Progression<T> {

    private final String formula;
    private final Function<Integer, Map<String, Object>> variableSupplier;
    private final Function<Double, T> converter;
    private final T fallback;

    public static FormulaProgression<Double> forDouble(String formula,
            Function<Integer, Map<String, Object>> variableSupplier,
            Double fallback) {
        return new FormulaProgression<>(formula, variableSupplier, d -> d, fallback);
    }

    public static FormulaProgression<Double> simple(String formula, Double fallback) {
        return forDouble(formula, level -> Map.of("level", level), fallback);
    }

    public FormulaProgression(String formula,
            Function<Integer, Map<String, Object>> variableSupplier,
            Function<Double, T> converter,
            T fallback) {
        this.formula = Texts.toStringSafe(formula);
        this.variableSupplier = variableSupplier == null ? (level -> Map.of()) : variableSupplier;
        this.converter = converter == null ? (d -> null) : converter;
        this.fallback = fallback;
    }

    @Override
    public T valueAt(int level) {
        if (Texts.isBlank(formula)) {
            return fallback;
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("level", level);
        Map<String, Object> supplied = variableSupplier.apply(level);
        if (supplied != null) {
            variables.putAll(supplied);
        }
        double result = ExpressionEngine.evaluate(formula, variables);
        if (!Double.isFinite(result)) {
            return fallback;
        }
        T converted = converter.apply(result);
        return converted != null ? converted : fallback;
    }

    public String formula() {
        return formula;
    }

    public T fallback() {
        return fallback;
    }
}
