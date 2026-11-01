package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.assembly.BaseNamePolicy;
import emaki.jiuwu.craft.corelib.api.assembly.EmakiLoreSectionContribution;
import emaki.jiuwu.craft.corelib.api.assembly.EmakiNameContribution;
import emaki.jiuwu.craft.corelib.api.assembly.EmakiStructuredPresentation;

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
                issues.add("名称贡献缺少 slot_id");
                continue;
            }
            String duplicateKey = Texts.lower(slotId);
            if (!seenNameSlots.add(duplicateKey)) {
                issues.add("重复的名称 slot_id: " + slotId);
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
                issues.add("Lore 段落缺少 section_id");
                continue;
            }
            String duplicateKey = Texts.lower(sectionId);
            if (!seenSectionIds.add(duplicateKey)) {
                issues.add("重复的 Lore section_id: " + sectionId);
                continue;
            }
            loreSections.add(section);
        }

        if (presentation.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE
                && Texts.isBlank(presentation.baseNameTemplate())) {
            issues.add("base_name_policy 为 EXPLICIT_TEMPLATE，但 base_name_template 为空");
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
