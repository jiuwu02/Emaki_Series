package emaki.jiuwu.craft.corelib.api.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Structured display definition of one namespace layer: how the item's base name is derived plus
 * the name and lore fragments the layer contributes.
 *
 * <p>The compact constructor normalizes the definition: a {@code null} policy becomes
 * {@link BaseNamePolicy#SOURCE_EFFECTIVE_NAME}, {@code baseNameTemplate} is stringified, name
 * contributions with a blank template are dropped, empty lore sections are dropped, and both
 * lists become immutable copies that are never {@code null}. The record is immutable once built.
 *
 * @param baseNamePolicy how the item's base display name is derived
 * @param baseNameTemplate template rendered as the base name under
 *                         {@link BaseNamePolicy#EXPLICIT_TEMPLATE}
 * @param nameContributions name fragments to layer onto the base name
 * @param loreSections lore blocks to append to the item's lore
 */
public record EmakiStructuredPresentation(BaseNamePolicy baseNamePolicy,
        String baseNameTemplate,
        List<EmakiNameContribution> nameContributions,
        List<EmakiLoreSectionContribution> loreSections) {

    /** Normalizes and filters the raw components as described in the type documentation. */
    public EmakiStructuredPresentation {
        baseNamePolicy = baseNamePolicy == null ? BaseNamePolicy.SOURCE_EFFECTIVE_NAME : baseNamePolicy;
        baseNameTemplate = Texts.toStringSafe(baseNameTemplate);
        nameContributions = copyNameContributions(nameContributions);
        loreSections = copyLoreSections(loreSections);
    }

    /**
     * {@return {@code true} when the definition carries no base name template, no name
     * contributions and no lore sections}
     */
    public boolean isEmpty() {
        return Texts.isBlank(baseNameTemplate) && nameContributions.isEmpty() && loreSections.isEmpty();
    }

    /**
     * {@return the definition as a plain map with the {@code base_name_policy},
     * {@code base_name_template}, {@code name_contributions} and {@code lore_sections} keys}
     *
     * <p>{@code base_name_template} is omitted when blank and {@code null} list elements are
     * skipped.
     */
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

    /**
     * Reads a definition back from its {@link #toMap()} form.
     *
     * <p>Malformed entries are skipped rather than failing the parse: contributions without a
     * stable id, lore sections without an id or without lines, and elements that are not maps.
     *
     * @param map plain map; {@code null} and empty are accepted
     * @return the parsed definition, or {@code null} when the map is {@code null} or empty, or
     *         when nothing usable was parsed (an empty definition is never returned); a missing
     *         policy falls back to {@link BaseNamePolicy#SOURCE_EFFECTIVE_NAME} and a missing
     *         template to an empty string
     */
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
