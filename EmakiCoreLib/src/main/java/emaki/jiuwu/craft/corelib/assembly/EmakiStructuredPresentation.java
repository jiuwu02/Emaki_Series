package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;

public record EmakiStructuredPresentation(BaseNamePolicy baseNamePolicy,
        String baseNameTemplate,
        List<EmakiNameContribution> nameContributions,
        List<EmakiLoreSectionContribution> loreSections) {

    public EmakiStructuredPresentation {
        baseNamePolicy = baseNamePolicy == null ? BaseNamePolicy.SOURCE_EFFECTIVE_NAME : baseNamePolicy;
        baseNameTemplate = Texts.toStringSafe(baseNameTemplate);
        nameContributions = copyNameContributions(nameContributions);
        loreSections = copyLoreSections(loreSections);
    }

    public boolean isEmpty() {
        return Texts.isBlank(baseNameTemplate) && nameContributions.isEmpty() && loreSections.isEmpty();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("base_name_policy", baseNamePolicy.name());
        if (Texts.isNotBlank(baseNameTemplate)) {
            map.put("base_name_template", baseNameTemplate);
        }
        List<Map<String, Object>> nameMaps = new ArrayList<>();
        for (EmakiNameContribution contribution : nameContributions) {
            if (contribution != null) {
                nameMaps.add(contribution.toMap());
            }
        }
        map.put("name_contributions", nameMaps);
        List<Map<String, Object>> loreMaps = new ArrayList<>();
        for (EmakiLoreSectionContribution section : loreSections) {
            if (section != null) {
                loreMaps.add(section.toMap());
            }
        }
        map.put("lore_sections", loreMaps);
        return map;
    }

    public static EmakiStructuredPresentation fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        List<EmakiNameContribution> nameContributions = new ArrayList<>();
        for (Object raw : ConfigNodes.asObjectList(map.get("name_contributions"))) {
            Object plain = ConfigNodes.toPlainData(raw);
            if (!(plain instanceof Map<?, ?> nameMap)) {
                continue;
            }
            EmakiNameContribution contribution = EmakiNameContribution.fromMap(normalizeMap(nameMap));
            if (contribution != null) {
                nameContributions.add(contribution);
            }
        }
        List<EmakiLoreSectionContribution> loreSections = new ArrayList<>();
        for (Object raw : ConfigNodes.asObjectList(map.get("lore_sections"))) {
            Object plain = ConfigNodes.toPlainData(raw);
            if (!(plain instanceof Map<?, ?> sectionMap)) {
                continue;
            }
            EmakiLoreSectionContribution section = EmakiLoreSectionContribution.fromMap(normalizeMap(sectionMap));
            if (section != null && !section.isEmpty()) {
                loreSections.add(section);
            }
        }
        EmakiStructuredPresentation presentation = new EmakiStructuredPresentation(
                BaseNamePolicy.fromValue(map.get("base_name_policy")),
                Texts.toStringSafe(map.get("base_name_template")),
                nameContributions,
                loreSections
        );
        return presentation.isEmpty() ? null : presentation;
    }

    private static List<EmakiNameContribution> copyNameContributions(List<EmakiNameContribution> contributions) {
        if (contributions == null || contributions.isEmpty()) {
            return List.of();
        }
        List<EmakiNameContribution> copied = new ArrayList<>();
        for (EmakiNameContribution contribution : contributions) {
            if (contribution != null && Texts.isNotBlank(contribution.contentTemplate())) {
                copied.add(contribution);
            }
        }
        return copied.isEmpty() ? List.of() : List.copyOf(copied);
    }

    private static List<EmakiLoreSectionContribution> copyLoreSections(List<EmakiLoreSectionContribution> sections) {
        if (sections == null || sections.isEmpty()) {
            return List.of();
        }
        List<EmakiLoreSectionContribution> copied = new ArrayList<>();
        for (EmakiLoreSectionContribution section : sections) {
            if (section != null && !section.isEmpty()) {
                copied.add(section);
            }
        }
        return copied.isEmpty() ? List.of() : List.copyOf(copied);
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> source) {
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
