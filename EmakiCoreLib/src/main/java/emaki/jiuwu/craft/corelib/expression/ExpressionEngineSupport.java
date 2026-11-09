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

final class ExpressionEngineSupport {

    static boolean validateExpressionLength(String expression) {
        return ExpressionRules.isLengthAllowed(expression);
}

    static boolean containsDangerousChars(String expression) {
        return ExpressionRules.containsDangerousChars(expression);
}

    static boolean hasRandomTextLines(Object config) {
        return ConfigNodes.get(config, "lines") != null;
}

    static boolean isRandomTextConfigType(String type) {
        return "random_text".equals(type)
                || "random_text_lines".equals(type)
                || "random_lines".equals(type)
                || "random_line".equals(type)
                || "text_lines".equals(type);
}

    static boolean isRandomCharConfigType(String type) {
        return "random_char".equals(type)
                || "random_chars".equals(type)
                || "char_random".equals(type)
                || "chars_random".equals(type);
}

    static boolean isWeightedRandomCharConfigType(String type) {
        return "weighted_random_char".equals(type)
                || "weighted_random_chars".equals(type)
                || "weighted_char".equals(type)
                || "weighted_chars".equals(type)
                || "weighted_char_random".equals(type);
}

    static boolean isConditionalCharConfigType(String type) {
        return "conditional_char".equals(type)
                || "condition_char".equals(type)
                || "case_char".equals(type)
                || "if_char".equals(type);
}

    static boolean looksLikeNumericConfig(Object config) {
        if (config == null) {
            return false;
        }
        if (ConfigNodes.get(config, "min") != null && ConfigNodes.get(config, "max") != null) {
            return true;
        }
        Object value = ConfigNodes.get(config, "value");
        return value instanceof Number;
}
}
