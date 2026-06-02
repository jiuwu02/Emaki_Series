package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class OperationTemplateRenderer {

    private static final Map<String, Pattern> REGEX_CACHE = new ConcurrentHashMap<>();

    public List<Map<String, Object>> normalizeOperations(Object raw) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        appendNormalizedOperations(normalized, raw);
        return normalized;
    }

    private void appendNormalizedOperations(List<Map<String, Object>> normalized, Object raw) {
        if (normalized == null || raw == null) {
            return;
        }
        Object plain = ConfigNodes.toPlainData(raw);
        if (plain instanceof Map<?, ?> map) {
            Map<String, Object> normalizedOperation = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                normalizedOperation.put(
                        String.valueOf(entry.getKey()),
                        ConfigNodes.toPlainData(entry.getValue())
                );
            }
            normalized.add(normalizedOperation);
            return;
        }
        if (plain instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                appendNormalizedOperations(normalized, entry);
            }
        }
    }

    public List<String> renderContent(Map<String, Object> operation, Map<String, Object> variables) {
        return renderTextLines(operation == null ? null : operation.get("content"), variables);
    }

    public Object resolveOperationValue(Map<String, Object> operation) {
        if (operation == null) {
            return "";
        }
        Object value = operation.get("value");
        if (value != null && Texts.isNotBlank(value)) {
            return value;
        }
        value = operation.get("replacement");
        return value == null || Texts.isBlank(value) ? operation.get("content") : value;
    }

    public Object resolveSearchPattern(Map<String, Object> operation) {
        if (operation == null) {
            return "";
        }
        Object pattern = operation.get("target_pattern");
        if (pattern != null && Texts.isNotBlank(pattern)) {
            return pattern;
        }
        pattern = operation.get("pattern");
        return pattern == null || Texts.isBlank(pattern) ? operation.get("anchor") : pattern;
    }

    public String renderTemplate(Object template, Map<String, Object> variables) {
        if (template instanceof String text) {
            return Texts.formatTemplate(text, variables == null ? Map.of() : variables);
        }
        return ExpressionEngine.evaluateStringConfig(template, variables == null ? Map.of() : variables);
    }

    public List<String> renderTextLines(Object raw, Map<String, Object> variables) {
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        if (raw instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            for (Object entry : iterable) {
                if (entry instanceof String text) {
                    result.add(Texts.formatTemplate(text, safeVariables));
                    continue;
                }
                result.addAll(ExpressionEngine.evaluateStringLinesConfig(entry, safeVariables));
            }
            return result;
        }
        return ExpressionEngine.evaluateStringLinesConfig(raw, safeVariables);
    }

    public static String replaceRegex(String text,
            String regex,
            String replacement,
            Map<String, Object> variables) {
        if (Texts.isBlank(regex)) {
            return Texts.toStringSafe(text);
        }
        try {
            Pattern pattern = REGEX_CACHE.computeIfAbsent(regex, Pattern::compile);
            Matcher matcher = pattern.matcher(Texts.toStringSafe(text));
            return matcher.replaceAll(Matcher.quoteReplacement(Texts.formatTemplate(
                    Texts.toStringSafe(replacement),
                    variables == null ? Map.of() : variables
            )));
        } catch (Exception _) {
            return Texts.toStringSafe(text);
        }
    }

    public static void clearRegexCache() {
        REGEX_CACHE.clear();
    }
}
