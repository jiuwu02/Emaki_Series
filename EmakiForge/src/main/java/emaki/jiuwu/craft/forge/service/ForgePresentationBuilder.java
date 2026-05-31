package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.assembly.EmakiLoreSectionContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiNameContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStructuredPresentation;
import emaki.jiuwu.craft.corelib.assembly.LocalNameState;
import emaki.jiuwu.craft.corelib.assembly.StructuredPresentationValidator;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

import org.bukkit.entity.Player;

final class ForgePresentationBuilder {

    private static final String NAMESPACE_ID = "forge";
    private static final int LORE_SECTION_ORDER = 100;

    private final StructuredPresentationValidator structuredValidator;

    ForgePresentationBuilder(StructuredPresentationValidator structuredValidator) {
        this.structuredValidator = structuredValidator;
    }

    EmakiStructuredPresentation buildPresentation(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            QualitySettings.QualityTier qualityTier,
            double multiplier,
            List<EmakiStatContribution> stats,
            QualitySettings settings,
            Player player) {
        return assemblePresentation(new LocalNameState(), List.of());
    }

    private EmakiStructuredPresentation assemblePresentation(LocalNameState nameState,
            List<String> loreLines) {
        List<EmakiNameContribution> nameContributions = new ArrayList<>();
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

    private void addSection(List<EmakiLoreSectionContribution> sections,
            String sectionId,
            int order,
            List<String> lines) {
        if (sections == null || Texts.isBlank(sectionId) || lines == null || lines.isEmpty()) {
            return;
        }
        sections.add(new EmakiLoreSectionContribution(sectionId, order, List.copyOf(lines), NAMESPACE_ID));
    }
}
