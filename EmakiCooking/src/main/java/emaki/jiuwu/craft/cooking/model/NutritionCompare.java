package emaki.jiuwu.craft.cooking.model;

import java.util.Locale;

public enum NutritionCompare {

    GREATER_OR_EQUAL(">="),
    GREATER(">"),
    LESS_OR_EQUAL("<="),
    LESS("<"),
    EQUAL("=="),
    NOT_EQUAL("!=");

    private final String symbol;

    NutritionCompare(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public boolean test(double value, double threshold) {
        return switch (this) {
            case GREATER_OR_EQUAL -> value >= threshold;
            case GREATER -> value > threshold;
            case LESS_OR_EQUAL -> value <= threshold;
            case LESS -> value < threshold;
            case EQUAL -> value == threshold;
            case NOT_EQUAL -> value != threshold;
        };
    }

    public static NutritionCompare parse(String raw) {
        if (raw == null) {
            return GREATER_OR_EQUAL;
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        return switch (token) {
            case ">=", "gte", "at_least", "ge" -> GREATER_OR_EQUAL;
            case ">", "gt", "greater" -> GREATER;
            case "<=", "lte", "at_most", "le" -> LESS_OR_EQUAL;
            case "<", "lt", "less" -> LESS;
            case "==", "=", "eq", "equal", "equals" -> EQUAL;
            case "!=", "<>", "ne", "not_equal" -> NOT_EQUAL;
            default -> GREATER_OR_EQUAL;
        };
    }
}
