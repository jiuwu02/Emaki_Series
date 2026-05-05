package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.BaseNamePolicy;
import emaki.jiuwu.craft.corelib.assembly.EmakiLoreSectionContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiNameContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStructuredPresentation;
import emaki.jiuwu.craft.corelib.assembly.NamePosition;
import emaki.jiuwu.craft.corelib.assembly.StructuredPresentationTemplateResolver;
import emaki.jiuwu.craft.corelib.assembly.StructuredPresentationValidator;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgePresentationBuilder {

    private static final String NAMESPACE_ID = "forge";
    private static final int NAME_ORDER_BASE = 100;
    private static final int LORE_SECTION_ORDER = 100;

    private final TextTemplateRenderer templateRenderer;
    private final NameModificationRegistry nameModifications;
    private final LoreActionRegistry loreActions;
    private final StructuredPresentationTemplateResolver structuredResolver;
    private final StructuredPresentationValidator structuredValidator;

    ForgePresentationBuilder(TextTemplateRenderer templateRenderer,
            NameModificationRegistry nameModifications,
            LoreActionRegistry loreActions,
            StructuredPresentationTemplateResolver structuredResolver,
            StructuredPresentationValidator structuredValidator) {
        this.templateRenderer = templateRenderer;
        this.nameModifications = nameModifications;
        this.loreActions = loreActions;
        this.structuredResolver = structuredResolver;
        this.structuredValidator = structuredValidator;
    }

    EmakiStructuredPresentation buildPresentation(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            QualitySettings.QualityTier qualityTier,
            double multiplier,
            List<EmakiStatContribution> stats,
            QualitySettings settings) {
        Map<String, Double> aggregatedStats = aggregateStats(stats);
        Map<String, Object> variables = templateRenderer.buildVariables(aggregatedStats, qualityTier, multiplier);
        LocalNameState nameState = new LocalNameState();
        ConfiguredStructuredState configuredState = new ConfiguredStructuredState();
        QualitySettings effectiveSettings = safeSettings(settings);

        EmakiStructuredPresentation recipePresentation = applyRecipePresentation(
                recipe,
                variables,
                configuredState,
                nameState
        );
        applyMaterialPresentations(materials, variables, configuredState, nameState);
        applyQualityPresentation(qualityTier, effectiveSettings, variables, configuredState, nameState);

        List<String> loreLines = buildLoreLines(
                recipe,
                materials,
                qualityTier,
                effectiveSettings,
                variables,
                recipePresentation
        );
        return assemblePresentation(configuredState, nameState, loreLines);
    }

    private EmakiStructuredPresentation applyRecipePresentation(Recipe recipe,
            Map<String, Object> variables,
            ConfiguredStructuredState configuredState,
            LocalNameState nameState) {
        if (recipe == null || recipe.result() == null) {
            return null;
        }
        EmakiStructuredPresentation recipePresentation = resolveStructuredPresentation(
                recipe.result().structuredPresentation(),
                variables
        );
        if (shouldMergeLegacyPresentation(recipePresentation)) {
            configuredState.merge(recipePresentation);
        }
        if (shouldApplyLegacyNameActions(recipePresentation)) {
            nameModifications.apply(nameState, recipe.result().nameModifications(), variables);
        }
        return recipePresentation;
    }

    private void applyMaterialPresentations(List<ForgeMaterialContribution> materials,
            Map<String, Object> variables,
            ConfiguredStructuredState configuredState,
            LocalNameState nameState) {
        if (materials == null) {
            return;
        }
        for (ForgeMaterialContribution material : materials) {
            if (material == null || material.material() == null) {
                continue;
            }
            ForgeMaterial forgeMaterial = material.material();
            List<Object> structuredFragments = forgeMaterial.structuredPresentations();
            if (!structuredFragments.isEmpty()) {
                for (Object rawStructured : structuredFragments) {
                    EmakiStructuredPresentation configuredPresentation = resolveStructuredPresentation(rawStructured, variables);
                    if (configuredPresentation != null) {
                        configuredState.merge(configuredPresentation);
                    }
                }
            }
            nameModifications.apply(nameState, forgeMaterial.nameModifications(), variables);
        }
    }

    private void applyQualityPresentation(QualitySettings.QualityTier qualityTier,
            QualitySettings settings,
            Map<String, Object> variables,
            ConfiguredStructuredState configuredState,
            LocalNameState nameState) {
        if (qualityTier == null || !settings.itemMetaEnabled()) {
            return;
        }
        EmakiStructuredPresentation configuredPresentation = resolveStructuredPresentation(
                settings.itemMetaStructuredPresentation(qualityTier.name()),
                variables
        );
        if (shouldMergeLegacyPresentation(configuredPresentation)) {
            configuredState.merge(configuredPresentation);
        }
        if (shouldApplyLegacyNameActions(configuredPresentation)) {
            nameModifications.apply(nameState, settings.itemMetaNameModifications(qualityTier.name()), variables);
        }
    }

    private List<String> buildLoreLines(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            QualitySettings.QualityTier qualityTier,
            QualitySettings settings,
            Map<String, Object> variables,
            EmakiStructuredPresentation recipePresentation) {
        List<String> loreLines = new ArrayList<>();
        if (recipe != null && recipe.result() != null) {
            if (recipePresentation == null || recipePresentation.loreSections().isEmpty()) {
                loreActions.apply(loreLines, recipe.result().loreActions(), variables);
            }
        }
        if (materials != null) {
            for (ForgeMaterialContribution material : materials) {
                if (material == null || material.material() == null) {
                    continue;
                }
                loreActions.apply(loreLines, material.material().loreActions(), variables);
            }
        }
        if (qualityTier != null && settings.itemMetaEnabled()) {
            EmakiStructuredPresentation configuredPresentation = resolveStructuredPresentation(
                    settings.itemMetaStructuredPresentation(qualityTier.name()),
                    variables
            );
            if (configuredPresentation == null || configuredPresentation.loreSections().isEmpty()) {
                loreActions.apply(loreLines, settings.itemMetaLoreActions(qualityTier.name()), variables);
            }
        }
        return loreLines;
    }

    private EmakiStructuredPresentation assemblePresentation(ConfiguredStructuredState configuredState,
            LocalNameState nameState,
            List<String> loreLines) {
        List<EmakiNameContribution> nameContributions = new ArrayList<>(configuredState.nameContributions());
        nameContributions.addAll(buildNameContributions(nameState));
        List<EmakiLoreSectionContribution> loreSections = new ArrayList<>(configuredState.loreSections());
        addSection(loreSections, "forge.display", LORE_SECTION_ORDER, loreLines);
        StructuredPresentationValidator.ValidationResult validation = structuredValidator.sanitize(new EmakiStructuredPresentation(
                configuredState.baseNamePolicyOr(nameState.baseNamePolicy()),
                configuredState.baseNameTemplateOr(nameState.baseNameTemplate()),
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

    private EmakiStructuredPresentation resolveStructuredPresentation(Object raw, Map<String, ?> variables) {
        StructuredPresentationValidator.ValidationResult validation = structuredValidator.sanitize(
                structuredResolver.fromConfig(raw, variables, NAMESPACE_ID)
        );
        return validation.presentation();
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

    private boolean shouldMergeLegacyPresentation(EmakiStructuredPresentation presentation) {
        return presentation != null
                && (presentation.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE
                || !presentation.nameContributions().isEmpty()
                || !presentation.loreSections().isEmpty());
    }

    private boolean shouldApplyLegacyNameActions(EmakiStructuredPresentation presentation) {
        return presentation == null
                || (presentation.baseNamePolicy() != BaseNamePolicy.EXPLICIT_TEMPLATE
                && presentation.nameContributions().isEmpty());
    }

    private QualitySettings safeSettings(QualitySettings settings) {
        return settings == null ? QualitySettings.defaults() : settings;
    }
}
