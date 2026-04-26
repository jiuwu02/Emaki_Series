package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class StructuredPresentationTemplateResolver {

    public EmakiStructuredPresentation fromConfig(Object raw,
            Map<String, ?> replacements,
            String defaultNamespace) {
        Object plain = ConfigNodes.toPlainData(raw);
        if (!(plain instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> safeReplacements = normalizeReplacements(replacements);
        EmakiStructuredPresentation presentation = EmakiStructuredPresentation.fromMap(resolveTextConfigs(
                normalizeMap(map),
                safeReplacements,
                defaultNamespace
        ));
        return resolve(presentation, replacements, defaultNamespace);
    }

    public EmakiStructuredPresentation resolve(EmakiStructuredPresentation presentation,
            Map<String, ?> replacements,
            String defaultNamespace) {
        if (presentation == null || presentation.isEmpty()) {
            return null;
        }
        Map<String, Object> safeReplacements = normalizeReplacements(replacements);
        List<EmakiNameContribution> resolvedNames = new ArrayList<>();
        for (EmakiNameContribution contribution : presentation.nameContributions()) {
            if (contribution == null) {
                continue;
            }
            resolvedNames.add(new EmakiNameContribution(
                    applyTemplate(contribution.slotId(), safeReplacements),
                    contribution.position(),
                    contribution.order(),
                    applyTemplate(contribution.contentTemplate(), safeReplacements),
                    resolveNamespace(contribution.sourceNamespace(), defaultNamespace, safeReplacements)
            ));
        }
        List<EmakiLoreSectionContribution> resolvedSections = new ArrayList<>();
        for (EmakiLoreSectionContribution section : presentation.loreSections()) {
            if (section == null) {
                continue;
            }
            List<String> lines = new ArrayList<>();
            for (String line : section.lines()) {
                lines.add(applyTemplate(line, safeReplacements));
            }
            resolvedSections.add(new EmakiLoreSectionContribution(
                    applyTemplate(section.sectionId(), safeReplacements),
                    section.order(),
                    lines,
                    resolveNamespace(section.sourceNamespace(), defaultNamespace, safeReplacements)
            ));
        }
        EmakiStructuredPresentation resolved = new EmakiStructuredPresentation(
                presentation.baseNamePolicy(),
                applyTemplate(presentation.baseNameTemplate(), safeReplacements),
                resolvedNames,
                resolvedSections
        );
        return resolved.isEmpty() ? null : resolved;
    }

    public EmakiStructuredPresentation merge(List<EmakiStructuredPresentation> presentations) {
        if (presentations == null || presentations.isEmpty()) {
            return null;
        }
        BaseNamePolicy baseNamePolicy = BaseNamePolicy.SOURCE_EFFECTIVE_NAME;
        String baseNameTemplate = "";
        List<EmakiNameContribution> nameContributions = new ArrayList<>();
        List<EmakiLoreSectionContribution> loreSections = new ArrayList<>();
        for (EmakiStructuredPresentation presentation : presentations) {
            if (presentation == null || presentation.isEmpty()) {
                continue;
            }
            if (presentation.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE
                    && Texts.isNotBlank(presentation.baseNameTemplate())) {
                baseNamePolicy = BaseNamePolicy.EXPLICIT_TEMPLATE;
                baseNameTemplate = presentation.baseNameTemplate();
            }
            nameContributions.addAll(presentation.nameContributions());
            loreSections.addAll(presentation.loreSections());
        }
        EmakiStructuredPresentation merged = new EmakiStructuredPresentation(
                baseNamePolicy,
                baseNameTemplate,
                nameContributions,
                loreSections
        );
        return merged.isEmpty() ? null : merged;
    }

    private String resolveNamespace(String configuredNamespace,
            String defaultNamespace,
            Map<String, Object> replacements) {
        String rendered = applyTemplate(configuredNamespace, replacements);
        return Texts.isBlank(rendered) ? Texts.toStringSafe(defaultNamespace) : rendered;
    }

    private String applyTemplate(String template, Map<String, Object> replacements) {
        return Texts.formatTemplate(Texts.toStringSafe(template), replacements);
    }

    private Map<String, Object> resolveTextConfigs(Map<String, Object> source,
            Map<String, Object> replacements,
            String defaultNamespace) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        if (result.containsKey("base_name_template")) {
            result.put("base_name_template", evaluateTextConfig(
                    result.get("base_name_template"),
                    replacements
            ));
        }
        List<Object> nameContributions = new ArrayList<>();
        for (Object rawName : ConfigNodes.asObjectList(result.get("name_contributions"))) {
            Object plain = ConfigNodes.toPlainData(rawName);
            if (!(plain instanceof Map<?, ?> map)) {
                nameContributions.add(plain);
                continue;
            }
            Map<String, Object> name = normalizeMap(map);
            replaceTextField(name, "slot_id", replacements);
            replaceTextField(name, "content_template", replacements);
            replaceTextField(name, "source_namespace", replacements);
            nameContributions.add(name);
        }
        result.put("name_contributions", nameContributions);

        List<Object> loreSections = new ArrayList<>();
        for (Object rawSection : ConfigNodes.asObjectList(result.get("lore_sections"))) {
            Object plain = ConfigNodes.toPlainData(rawSection);
            if (!(plain instanceof Map<?, ?> map)) {
                loreSections.add(plain);
                continue;
            }
            Map<String, Object> section = normalizeMap(map);
            replaceTextField(section, "section_id", replacements);
            replaceTextField(section, "source_namespace", replacements);
            if (!section.containsKey("source_namespace") && Texts.isNotBlank(defaultNamespace)) {
                section.put("source_namespace", defaultNamespace);
            }
            if (section.containsKey("lines")) {
                section.put("lines", evaluateTextLinesConfig(section.get("lines"), replacements));
            }
            loreSections.add(section);
        }
        result.put("lore_sections", loreSections);
        return result;
    }

    private void replaceTextField(Map<String, Object> map, String key, Map<String, Object> replacements) {
        if (map != null && map.containsKey(key)) {
            map.put(key, evaluateTextConfig(map.get(key), replacements));
        }
    }

    private String evaluateTextConfig(Object raw, Map<String, Object> replacements) {
        if (raw instanceof String text) {
            return Texts.formatTemplate(text, replacements);
        }
        return ExpressionEngine.evaluateStringConfig(raw, replacements);
    }

    private List<String> evaluateTextLinesConfig(Object raw, Map<String, Object> replacements) {
        if (raw instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            for (Object entry : iterable) {
                if (entry instanceof String text) {
                    result.add(Texts.formatTemplate(text, replacements));
                    continue;
                }
                result.addAll(ExpressionEngine.evaluateStringLinesConfig(entry, replacements));
            }
            return result;
        }
        return ExpressionEngine.evaluateStringLinesConfig(raw, replacements);
    }

    private Map<String, Object> normalizeReplacements(Map<String, ?> replacements) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (replacements == null) {
            return normalized;
        }
        for (Map.Entry<String, ?> entry : replacements.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey(), entry.getValue());
        }
        return normalized;
    }

    private Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(String.valueOf(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
        }
        return result;
    }
}
