package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.assembly.BaseNamePolicy;
import emaki.jiuwu.craft.corelib.assembly.EmakiLoreSectionContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiNameContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStructuredPresentation;
import emaki.jiuwu.craft.corelib.text.Texts;

final class ConfiguredStructuredState {

    private BaseNamePolicy baseNamePolicy = BaseNamePolicy.SOURCE_EFFECTIVE_NAME;
    private String baseNameTemplate = "";
    private final List<EmakiNameContribution> nameContributions = new ArrayList<>();
    private final List<EmakiLoreSectionContribution> loreSections = new ArrayList<>();

    void merge(EmakiStructuredPresentation presentation) {
        if (presentation == null || presentation.isEmpty()) {
            return;
        }
        if (presentation.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE
                && Texts.isNotBlank(presentation.baseNameTemplate())) {
            baseNamePolicy = BaseNamePolicy.EXPLICIT_TEMPLATE;
            baseNameTemplate = presentation.baseNameTemplate();
        }
        nameContributions.addAll(presentation.nameContributions());
        loreSections.addAll(presentation.loreSections());
    }

    List<EmakiNameContribution> nameContributions() {
        return nameContributions;
    }

    List<EmakiLoreSectionContribution> loreSections() {
        return loreSections;
    }

    BaseNamePolicy baseNamePolicyOr(BaseNamePolicy fallback) {
        return baseNamePolicy == BaseNamePolicy.EXPLICIT_TEMPLATE ? baseNamePolicy : fallback;
    }

    String baseNameTemplateOr(String fallback) {
        return baseNamePolicy == BaseNamePolicy.EXPLICIT_TEMPLATE ? baseNameTemplate : fallback;
    }
}
