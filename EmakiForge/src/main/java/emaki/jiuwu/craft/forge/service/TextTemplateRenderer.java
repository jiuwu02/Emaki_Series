package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.model.QualitySettings;

final class TextTemplateRenderer {

    List<Map<String, Object>> normalizeOperations(Object raw) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object operation : ConfigNodes.asObjectList(raw)) {
            Object plain = ConfigNodes.toPlainData(operation);
            if (!(plain instanceof Map<?, ?> map)) {
                continue;
            }
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
        }
        return normalized;
    }

    Map<String, Object> buildVariables(Map<String, Double> aggregatedStats,
            QualitySettings.QualityTier qualityTier,
            double multiplier) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (aggregatedStats != null) {
            for (Map.Entry<String, Double> entry : aggregatedStats.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                variables.put(entry.getKey(), Numbers.formatNumber(entry.getValue(), "0.##"));
            }
        }
        String qualityName = qualityTier == null ? "" : qualityTier.name();
        variables.put("quality", qualityName);
        variables.put("quality_name", qualityName);
        variables.put("quality_multiplier", Numbers.formatNumber(multiplier, "0.##"));
        variables.put("multiplier", Numbers.formatNumber(multiplier, "0.##"));
        return variables;
    }

    List<String> renderContent(Map<String, Object> operation, Map<String, Object> variables) {
        return renderTextLines(operation == null ? null : operation.get("content"), variables);
    }

    Object resolveOperationValue(Map<String, Object> operation) {
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

    Object resolveSearchPattern(Map<String, Object> operation) {
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

    String renderTemplate(Object template, Map<String, Object> variables) {
        if (template instanceof String text) {
            return Texts.formatTemplate(text, variables == null ? Map.of() : variables);
        }
        return ExpressionEngine.evaluateStringConfig(template, variables == null ? Map.of() : variables);
    }

    List<String> renderTextLines(Object raw, Map<String, Object> variables) {
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

    static String replaceRegex(String text,
            String regex,
            String replacement,
            Map<String, Object> variables) {
        if (Texts.isBlank(regex)) {
            return Texts.toStringSafe(text);
        }
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(Texts.toStringSafe(text));
            return matcher.replaceAll(Matcher.quoteReplacement(Texts.formatTemplate(
                    Texts.toStringSafe(replacement),
                    variables == null ? Map.of() : variables
            )));
        } catch (Exception _) {
            return Texts.toStringSafe(text);
        }
    }
}
