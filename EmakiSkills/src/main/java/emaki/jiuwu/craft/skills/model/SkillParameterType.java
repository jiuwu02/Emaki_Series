package emaki.jiuwu.craft.skills.model;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public enum SkillParameterType {
    STRING,
    RANDOM_TEXT,
    RANDOM_CHAR,
    WEIGHTED_RANDOM_CHAR,
    CONDITIONAL_CHAR,
    BOOLEAN,
    CONSTANT,
    RANGE,
    UNIFORM,
    GAUSSIAN,
    SKEW_NORMAL,
    TRIANGLE,
    EXPRESSION;

    public static SkillParameterType fromString(String value) {
        return switch (Texts.lower(value).replace('-', '_')) {
            case "string", "str", "text" -> STRING;
            case "random_text", "random_text_lines", "random_lines", "random_line", "text_lines" -> RANDOM_TEXT;
            case "random_char", "random_chars", "char_random", "chars_random" -> RANDOM_CHAR;
            case "weighted_random_char", "weighted_random_chars", "weighted_char", "weighted_chars", "weighted_char_random" -> WEIGHTED_RANDOM_CHAR;
            case "conditional_char", "condition_char", "case_char", "if_char" -> CONDITIONAL_CHAR;
            case "boolean", "bool", "flag" -> BOOLEAN;
            case "constant", "const", "fixed" -> CONSTANT;
            case "range" -> RANGE;
            case "uniform" -> UNIFORM;
            case "gaussian", "normal" -> GAUSSIAN;
            case "skew_normal", "skewnormal" -> SKEW_NORMAL;
            case "triangle" -> TRIANGLE;
            case "expression", "expr", "formula" -> EXPRESSION;
            default -> CONSTANT;
        };
    }

    public boolean numeric() {
        return this != STRING && this != RANDOM_TEXT && this != RANDOM_CHAR
                && this != WEIGHTED_RANDOM_CHAR && this != CONDITIONAL_CHAR && this != BOOLEAN;
    }

    public String configType() {
        return switch (this) {
            case RANDOM_TEXT -> "random_text";
            case RANDOM_CHAR -> "random_char";
            case WEIGHTED_RANDOM_CHAR -> "weighted_random_char";
            case CONDITIONAL_CHAR -> "conditional_char";
            case CONSTANT -> "constant";
            case RANGE -> "range";
            case UNIFORM -> "uniform";
            case GAUSSIAN -> "gaussian";
            case SKEW_NORMAL -> "skew_normal";
            case TRIANGLE -> "triangle";
            case EXPRESSION -> "expression";
            default -> "";
        };
    }
}
