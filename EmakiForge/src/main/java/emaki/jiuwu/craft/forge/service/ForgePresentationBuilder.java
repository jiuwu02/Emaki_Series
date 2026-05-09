package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.EmakiLoreSectionContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiNameContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStructuredPresentation;
import emaki.jiuwu.craft.corelib.assembly.LocalNameState;
import emaki.jiuwu.craft.corelib.assembly.LoreOperationRegistry;
import emaki.jiuwu.craft.corelib.assembly.NameOperationRegistry;
import emaki.jiuwu.craft.corelib.assembly.NamePosition;
import emaki.jiuwu.craft.corelib.assembly.OperationTemplateRenderer;
import emaki.jiuwu.craft.corelib.assembly.StructuredPresentationValidator;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgePresentationBuilder {

    private static final String NAMESPACE_ID = "forge";
    private static final int NAME_ORDER_BASE = 100;
    private static final int LORE_SECTION_ORDER = 100;

    private final OperationTemplateRenderer templateRenderer;
    private final NameOperationRegistry nameOperations;
    private final LoreOperationRegistry loreOperations;
    private final StructuredPresentationValidator structuredValidator;

    ForgePresentationBuilder(OperationTemplateRenderer templateRenderer,
            NameOperationRegistry nameOperations,
            LoreOperationRegistry loreOperations,
            StructuredPresentationValidator structuredValidator) {
        this.templateRenderer = templateRenderer;
        this.nameOperations = nameOperations;
        this.loreOperations = loreOperations;
        this.structuredValidator = structuredValidator;
    }

    EmakiStructuredPresentation buildPresentation(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            QualitySettings.QualityTier qualityTier,
            double multiplier,
            List<EmakiStatContribution> stats,
            QualitySettings settings) {
        Map<String, Double> aggregatedStats = aggregateStats(stats);
        Map<String, Object> variables = buildVariables(aggregatedStats, qualityTier, multiplier);
        LocalNameState nameState = new LocalNameState();
        QualitySettings effectiveSettings = safeSettings(settings);

        applyRecipePresentation(recipe, variables, nameState);
        applyMaterialPresentations(materials, variables, nameState);

        List<String> loreLines = buildLoreLines(recipe, materials, variables);
        applyQualityPresentation(qualityTier, effectiveSettings, variables, nameState, loreLines);

        return assemblePresentation(nameState, loreLines);
    }

    private void applyRecipePresentation(Recipe recipe,
            Map<String, Object> variables,
            LocalNameState nameState) {
        if (recipe == null || recipe.result() == null) {
            return;
        }
        nameOperations.apply(nameState, recipe.result().nameModifications(), variables);
    }

    private void applyMaterialPresentations(List<ForgeMaterialContribution> materials,
            Map<String, Object> variables,
            LocalNameState nameState) {
        if (materials == null) {
            return;
        }
        for (ForgeMaterialContribution material : materials) {
            if (material == null || material.material() == null) {
                continue;
            }
            ForgeMaterial forgeMaterial = material.material();
            nameOperations.apply(nameState, forgeMaterial.nameModifications(), variables);
        }
    }

    private void applyQualityPresentation(QualitySettings.QualityTier qualityTier,
            QualitySettings settings,
            Map<String, Object> variables,
            LocalNameState nameState,
            List<String> loreLines) {
        if (qualityTier == null || !settings.itemMetaEnabled()) {
            return;
        }
        nameOperations.apply(nameState, settings.itemMetaNameActions(qualityTier.name()), variables);
        loreOperations.apply(loreLines, settings.itemMetaLoreActions(qualityTier.name()), variables);
    }

    private List<String> buildLoreLines(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            Map<String, Object> variables) {
        List<String> loreLines = new ArrayList<>();
        if (recipe != null && recipe.result() != null) {
            loreOperations.apply(loreLines, recipe.result().loreActions(), variables);
        }
        if (materials != null) {
            for (ForgeMaterialContribution material : materials) {
                if (material == null || material.material() == null) {
                    continue;
                }
                loreOperations.apply(loreLines, material.material().loreActions(), variables);
            }
        }
        return loreLines;
    }

    private EmakiStructuredPresentation assemblePresentation(LocalNameState nameState,
            List<String> loreLines) {
        List<EmakiNameContribution> nameContributions = new ArrayList<>(buildNameContributions(nameState));
        List<EmakiLoreSectionContribution> loreSections = new ArrayList<>();
        addSection(loreSections, "forge.display", LORE_SECTION_ORDER, loreLines);
        StructuredPresentationValidator.ValidationResult validation = structuredValidator.sanitize(new EmakiStructuredPresentation(
                nameState.baseNamePolicy(),
                nameState.baseNameTemplate(),
                nameContributions,
                loreSections
        ));
        EmakiStructuredPresentation presentation = validation.presentation();
        return presentation == null || presentation.isEmpty() ? null : presentation;
    }

    private Map<String, Double> aggregateStats(List<EmakiStatContribution> stats) {
        Map<String, Double> aggregated = new LinkedHashMap<>();
        if (stats == null) {
            return aggregated;
        }
        for (EmakiStatContribution contribution : stats) {
            if (contribution == null || Texts.isBlank(contribution.statId())) {
                continue;
            }
            aggregated.merge(Texts.lower(contribution.statId()), contribution.amount(), Double::sum);
        }
        return aggregated;
    }

    private Map<String, Object> buildVariables(Map<String, Double> aggregatedStats,
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

    private List<EmakiNameContribution> buildNameContributions(LocalNameState state) {
        if (state == null) {
            return List.of();
        }
        List<EmakiNameContribution> contributions = new ArrayList<>();
        int order = NAME_ORDER_BASE;
        int index = 0;
        for (String prefix : state.prefixes()) {
            contributions.add(new EmakiNameContribution(
                    stableNameSlotId("prefix", index++),
                    NamePosition.PREFIX,
                    order++,
                    prefix,
                    NAMESPACE_ID
            ));
        }
        index = 0;
        for (String postfix : state.postfixes()) {
            contributions.add(new EmakiNameContribution(
                    stableNameSlotId("postfix", index++),
                    NamePosition.POSTFIX,
                    order++,
                    postfix,
                    NAMESPACE_ID
            ));
        }
        return contributions;
    }

    private String stableNameSlotId(String role, int index) {
        return index <= 0 ? NAMESPACE_ID + "." + role : NAMESPACE_ID + "." + role + "." + index;
    }

    private void addSection(List<EmakiLoreSectionContribution> sections,
            String sectionId,
            int order,
            List<String> lines) {
        if (sections == null || Texts.isBlank(sectionId) || lines == null || lines.isEmpty()) {
            return;
        }
        sections.add(new EmakiLoreSectionContribution(sectionId, order, List.copyOf(lines), NAMESPACE_ID));
    }

    private QualitySettings safeSettings(QualitySettings settings) {
        return settings == null ? QualitySettings.defaults() : settings;
    }
}
