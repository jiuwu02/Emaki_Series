package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class StructuredPresentationValidator {

    public ValidationResult sanitize(EmakiStructuredPresentation presentation) {
        if (presentation == null || presentation.isEmpty()) {
            return ValidationResult.empty();
        }
        List<String> issues = new ArrayList<>();
        List<EmakiNameContribution> nameContributions = new ArrayList<>();
        Set<String> seenNameSlots = new LinkedHashSet<>();
        for (EmakiNameContribution contribution : presentation.nameContributions()) {
            if (contribution == null || Texts.isBlank(contribution.contentTemplate())) {
                continue;
            }
            String slotId = Texts.trim(contribution.slotId());
            if (Texts.isBlank(slotId)) {
                issues.add("name contribution is missing slot_id");
                continue;
            }
            String duplicateKey = Texts.lower(slotId);
            if (!seenNameSlots.add(duplicateKey)) {
                issues.add("duplicate name slot_id: " + slotId);
                continue;
            }
            nameContributions.add(contribution);
        }

        List<EmakiLoreSectionContribution> loreSections = new ArrayList<>();
        Set<String> seenSectionIds = new LinkedHashSet<>();
        for (EmakiLoreSectionContribution section : presentation.loreSections()) {
            if (section == null || section.isEmpty()) {
                continue;
            }
            String sectionId = Texts.trim(section.sectionId());
            if (Texts.isBlank(sectionId)) {
                issues.add("lore section is missing section_id");
                continue;
            }
            String duplicateKey = Texts.lower(sectionId);
            if (!seenSectionIds.add(duplicateKey)) {
                issues.add("duplicate lore section_id: " + sectionId);
                continue;
            }
            loreSections.add(section);
        }

        if (presentation.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE
                && Texts.isBlank(presentation.baseNameTemplate())) {
            issues.add("base_name_policy is EXPLICIT_TEMPLATE but base_name_template is blank");
        }

        EmakiStructuredPresentation sanitized = new EmakiStructuredPresentation(
                presentation.baseNamePolicy(),
                presentation.baseNameTemplate(),
                nameContributions,
                loreSections
        );
        return sanitized.isEmpty() ? ValidationResult.empty() : new ValidationResult(sanitized, List.copyOf(issues));
    }

    public record ValidationResult(EmakiStructuredPresentation presentation, List<String> issues) {

        public ValidationResult {
            presentation = presentation == null || presentation.isEmpty() ? null : presentation;
            issues = issues == null ? List.of() : List.copyOf(issues);
        }

        public static ValidationResult empty() {
            return new ValidationResult(null, List.of());
        }
    }
}
