package emaki.jiuwu.craft.skills.model;

import emaki.jiuwu.craft.corelib.text.Texts;

public enum SkillParameterType {
    STRING,
    RANDOM_TEXT,
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
        return this != STRING && this != RANDOM_TEXT && this != BOOLEAN;
    }

    public String configType() {
        return switch (this) {
            case RANDOM_TEXT -> "random_text";
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
