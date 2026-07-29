package emaki.jiuwu.craft.corelib.expression;

import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.text.Texts;

final class ExpressionRules {

    static final int MAX_EXPRESSION_LENGTH = 256;
    static final int MAX_NESTED_DEPTH = 10;

    private static final Pattern NON_NUMERIC_EXPRESSION_PATTERN = Pattern.compile("[^0-9.\\s+\\-*/%^(),eE]");
    private static final Pattern DANGEROUS_CHAR_PATTERN = Pattern.compile("[`$\\\\]");

    private ExpressionRules() {
    }

    static boolean isLengthAllowed(String expression) {
        return expression != null && expression.length() <= MAX_EXPRESSION_LENGTH;
    }

    static boolean containsDangerousChars(String expression) {
        return expression != null && DANGEROUS_CHAR_PATTERN.matcher(expression).find();
    }

    static boolean isPureNumericExpression(String expression) {
        if (Texts.isBlank(expression)) {
            return false;
        }
        String lowered = Texts.lower(expression)
                .replace("ceil", "")
                .replace("floor", "")
                .replace("round", "")
                .replace("log10", "")
                .replace("min", "")
                .replace("max", "")
                .replace("pow", "");
        return !NON_NUMERIC_EXPRESSION_PATTERN.matcher(lowered).find();
    }
}
