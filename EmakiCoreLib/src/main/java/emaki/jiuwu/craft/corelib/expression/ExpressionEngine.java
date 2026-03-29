package emaki.jiuwu.craft.corelib.expression;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.math.Randoms;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

public final class ExpressionEngine {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{(\\w+)}");
    private static final Pattern RANGE_PATTERN = Pattern.compile("^\\s*(.+)\\s*~\\s*(.+)\\s*$");

    private ExpressionEngine() {
    }

    public static double evaluate(String expression) {
        return evaluate(expression, Map.of());
    }

    public static double evaluate(String expression, Map<String, ?> variables) {
        if (Texts.isBlank(expression)) {
            return 0D;
        }
        String prepared = replaceVariables(expression, variables);
        try {
            Expression built = new ExpressionBuilder(prepared)
                .functions(customFunctions())
                .build();
            return built.evaluate();
        } catch (Exception ignored) {
            return 0D;
        }
    }

    public static double evaluateRandomConfig(Object config) {
        if (config == null) {
            return 0D;
        }
        if (config instanceof Number number) {
            return number.doubleValue();
        }
        if (config instanceof String text) {
            return evaluateNumericText(text);
        }
        String type = Texts.lower(ConfigNodes.string(config, "type", ""));
        if (Texts.isBlank(type)) {
            Object value = ConfigNodes.get(config, "value");
            if (value != null) {
                return evaluateRandomConfig(value);
            }
            if (ConfigNodes.get(config, "min") != null && ConfigNodes.get(config, "max") != null) {
                return evaluateUniform(config);
            }
            Object expression = ConfigNodes.get(config, "expression");
            if (expression != null) {
                return evaluate(Texts.toStringSafe(expression));
            }
            return 0D;
        }
        return switch (type) {
            case "constant", "const", "fixed" -> evaluateRandomConfig(ConfigNodes.get(config, "value"));
            case "uniform" -> evaluateUniform(config);
            case "gaussian", "normal" -> evaluateGaussian(config);
            case "skew_normal" -> evaluateSkewNormal(config);
            case "triangle" -> evaluateTriangle(config);
            case "expression" -> evaluate(Texts.toStringSafe(ConfigNodes.get(config, "expression")));
            default -> Numbers.tryParseDouble(config, 0D);
        };
    }

    private static String replaceVariables(String expression, Map<String, ?> variables) {
        if (variables == null || variables.isEmpty()) {
            return expression;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(expression);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(Texts.toStringSafe(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static double evaluateNumericText(String text) {
        Matcher matcher = RANGE_PATTERN.matcher(text);
        if (matcher.matches()) {
            return Randoms.uniform(evaluate(matcher.group(1)), evaluate(matcher.group(2)));
        }
        if (Numbers.isNumeric(text)) {
            return Numbers.tryParseDouble(text, 0D);
        }
        return evaluate(text);
    }

    private static double evaluateUniform(Object config) {
        double min = evaluateRandomConfig(ConfigNodes.get(config, "min"));
        double max = evaluateRandomConfig(ConfigNodes.get(config, "max"));
        return Randoms.uniform(min, max);
    }

    private static double evaluateGaussian(Object config) {
        Double min = optionalNumber(ConfigNodes.get(config, "min"));
        Double max = optionalNumber(ConfigNodes.get(config, "max"));
        Double mean = optionalNumber(ConfigNodes.get(config, "mean"));
        if (mean == null) {
            mean = min != null && max != null ? (min + max) / 2D : 0D;
        }
        Double stdDev = optionalNumber(ConfigNodes.get(config, "std_dev"));
        if (stdDev == null) {
            stdDev = optionalNumber(ConfigNodes.get(config, "std-dev"));
        }
        if (stdDev == null) {
            stdDev = min != null && max != null ? Math.abs(max - min) / 6D : 1D;
        }
        int maxAttempts = Numbers.tryParseInt(ConfigNodes.get(config, "max_attempts"), 128);
        return Randoms.gaussian(mean, stdDev, min, max, maxAttempts);
    }

    private static double evaluateSkewNormal(Object config) {
        Double min = optionalNumber(ConfigNodes.get(config, "min"));
        Double max = optionalNumber(ConfigNodes.get(config, "max"));
        Double mean = optionalNumber(ConfigNodes.get(config, "mean"));
        if (mean == null) {
            mean = min != null && max != null ? (min + max) / 2D : 0D;
        }
        Double stdDev = optionalNumber(ConfigNodes.get(config, "std_dev"));
        if (stdDev == null) {
            stdDev = optionalNumber(ConfigNodes.get(config, "std-dev"));
        }
        if (stdDev == null) {
            stdDev = min != null && max != null ? Math.abs(max - min) / 6D : 1D;
        }
        Double skewness = optionalNumber(ConfigNodes.get(config, "skewness"));
        int maxAttempts = Numbers.tryParseInt(ConfigNodes.get(config, "max_attempts"), 128);
        return Randoms.skewNormal(mean, stdDev, skewness == null ? 0D : skewness, min, max, maxAttempts);
    }

    private static double evaluateTriangle(Object config) {
        Double mode = optionalNumber(ConfigNodes.get(config, "mode"));
        Double deviation = optionalNumber(ConfigNodes.get(config, "deviation"));
        return Randoms.triangle(mode == null ? 0D : mode, deviation == null ? 1D : deviation);
    }

    private static Double optionalNumber(Object value) {
        if (value == null) {
            return null;
        }
        return evaluateRandomConfig(value);
    }

    private static Function[] customFunctions() {
        return new Function[]{
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
    }
}
