package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.corelib.api.text.Texts;

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

    public List<String> renderContent(Map<String, Object> operation,
            Map<String, Object> variables,
            ActionContext context,
            DebugLogger debugLogger,
            String source) {
        return renderTextLines(operation == null ? null : operation.get("content"), variables, context, debugLogger, source);
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

    public String renderTemplate(Object template,
            Map<String, Object> variables,
            ActionContext context,
            DebugLogger debugLogger,
            String source) {
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        String rendered = template instanceof String text
                ? PlaceholderRenderer.renderInternal(text, safeVariables, debugLogger, context == null ? null : context.player(), source)
                : ExpressionEngine.evaluateStringConfig(template, safeVariables);
        return PlaceholderRenderer.renderPapi(context == null ? null : context.player(), rendered, debugLogger, source);
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

    public List<String> renderTextLines(Object raw,
            Map<String, Object> variables,
            ActionContext context,
            DebugLogger debugLogger,
            String source) {
        Map<String, Object> safeVariables = variables == null ? Map.of() : variables;
        if (raw instanceof String text) {
            String rendered = renderTemplate(text, safeVariables, context, debugLogger, source);
            return rendered.isEmpty() ? List.of() : List.of(rendered);
        }
        if (raw instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            for (Object entry : iterable) {
                if (entry instanceof String text) {
                    result.add(renderTemplate(text, safeVariables, context, debugLogger, source));
                    continue;
                }
                for (String line : ExpressionEngine.evaluateStringLinesConfig(entry, safeVariables)) {
                    result.add(PlaceholderRenderer.renderPapi(context == null ? null : context.player(), line, debugLogger, source));
                }
            }
            return result;
        }
        List<String> lines = ExpressionEngine.evaluateStringLinesConfig(raw, safeVariables);
        if (lines.isEmpty()) {
            return lines;
        }
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(PlaceholderRenderer.renderPapi(context == null ? null : context.player(), line, debugLogger, source));
        }
        return result;
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
