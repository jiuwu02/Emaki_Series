package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.BaseNamePolicy;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.assembly.EmakiLoreSectionContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiNameContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStructuredPresentation;
import emaki.jiuwu.craft.corelib.assembly.StructuredPresentationTemplateResolver;
import emaki.jiuwu.craft.corelib.assembly.StructuredPresentationValidator;
import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.model.StrengthenState;

public final class StrengthenSnapshotBuilder {

    private static final String NAMESPACE_ID = "strengthen";
    private static final int STATS_PRIMARY_ORDER = 210;
    private static final int EFFECTS_ORDER = 230;
    private static final int STATS_SECONDARY_ORDER = 240;
    private final StructuredPresentationTemplateResolver structuredResolver = new StructuredPresentationTemplateResolver();
    private final StructuredPresentationValidator structuredValidator = new StructuredPresentationValidator();

    public EmakiItemLayerSnapshot buildLayerSnapshot(StrengthenRecipe recipe,
            StrengthenState state,
            String materialsSignature) {
        if (recipe == null || state == null) {
            return null;
        }
        Map<String, Double> stats = recipe.cumulativeStats(state.currentStar());
        Map<String, Object> variables = buildVariables(recipe, state, stats);
        EmakiStructuredPresentation structuredPresentation = buildStructuredPresentation(recipe, state, stats, variables);
        return new EmakiItemLayerSnapshot(
                NAMESPACE_ID,
                1,
                buildAudit(recipe, state, materialsSignature),
                buildStatContributions(stats),
                structuredPresentation == null || structuredPresentation.isEmpty() ? null : structuredPresentation
        );
    }

    private Map<String, Object> buildAudit(StrengthenRecipe recipe, StrengthenState state, String materialsSignature) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("recipe_id", recipe.id());
        audit.put("current_star", state.currentStar());
        audit.put("crack_level", state.crackLevel());
        audit.put("temper", state.temperLevel());
        audit.put("star", state.currentStar());
        audit.put("max_temper", recipe.limits().maxTemper());
        audit.put("success_count", state.successCount());
        audit.put("failure_count", state.failureCount());
        audit.put("first_reach_flags", new ArrayList<>(state.firstReachFlags()));
        audit.put("last_attempt_at", state.lastAttemptAt());
        audit.put("materials_signature", Texts.toStringSafe(materialsSignature));
        audit.put("base_source_signature", state.baseSourceSignature());
        return audit;
    }

    private List<EmakiStatContribution> buildStatContributions(Map<String, Double> stats) {
        List<EmakiStatContribution> result = new ArrayList<>();
        int sequence = 0;
        for (Map.Entry<String, Double> entry : stats.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || Math.abs(entry.getValue()) <= 1.0E-9D) {
                continue;
            }
            result.add(new EmakiStatContribution(entry.getKey(), entry.getValue(), NAMESPACE_ID + ":" + entry.getKey(), sequence++));
        }
        return result;
    }

    private EmakiStructuredPresentation buildStructuredPresentation(StrengthenRecipe recipe,
            StrengthenState state,
            Map<String, Double> stats,
            Map<String, Object> variables) {
        EmakiStructuredPresentation configuredPresentation = resolveConfiguredPresentation(recipe.structuredPresentation(), variables);
        BaseNamePolicy baseNamePolicy = configuredPresentation == null
                ? BaseNamePolicy.SOURCE_EFFECTIVE_NAME
                : configuredPresentation.baseNamePolicy();
        String baseNameTemplate = configuredPresentation == null ? "" : configuredPresentation.baseNameTemplate();
        List<EmakiNameContribution> nameContributions = new ArrayList<>();
        List<EmakiLoreSectionContribution> loreSections = new ArrayList<>();
        if (configuredPresentation != null) {
            nameContributions.addAll(configuredPresentation.nameContributions());
            loreSections.addAll(configuredPresentation.loreSections());
        }
        List<String> effectLines = new ArrayList<>();
        for (StrengthenRecipe.StarStage stage : recipe.reachedStages(state.currentStar())) {
            if (stage == null) {
                continue;
            }
            if (Texts.isNotBlank(stage.name())) {
                effectLines.add("<gold>星级效果: " + renderTemplate(stage.name(), variables) + "</gold>");
            }
            EmakiStructuredPresentation stagePresentation = resolveConfiguredPresentation(stage.structuredPresentation(), variables);
            if (stagePresentation == null) {
                continue;
            }
            if (hasExplicitBaseName(stagePresentation)) {
                baseNamePolicy = stagePresentation.baseNamePolicy();
                baseNameTemplate = stagePresentation.baseNameTemplate();
            }
            nameContributions.addAll(stagePresentation.nameContributions());
            loreSections.addAll(stagePresentation.loreSections());
        }
        addSection(loreSections, "strengthen.effects", EFFECTS_ORDER, effectLines);
        loreSections.addAll(buildStatSections(recipe, stats, variables));
        StructuredPresentationValidator.ValidationResult validation = structuredValidator.sanitize(new EmakiStructuredPresentation(
                baseNamePolicy,
                baseNameTemplate,
                nameContributions,
                loreSections
        ));
        return validation.presentation();
    }

    private List<EmakiLoreSectionContribution> buildStatSections(StrengthenRecipe recipe,
            Map<String, Double> stats,
            Map<String, Object> variables) {
        Map<SectionTarget, List<String>> statSectionLines = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : stats.entrySet()) {
            if (Math.abs(entry.getValue()) <= 1.0E-9D) {
                continue;
            }
            String statId = Texts.lower(entry.getKey());
            StrengthenRecipe.StatLineDefinition definition = recipe.statLines().get(statId);
            String renderedLine = renderStatLine(definition, statId, entry.getValue(), variables);
            if (Texts.isBlank(renderedLine)) {
                continue;
            }
            SectionTarget sectionTarget = resolveStatSection(definition);
            statSectionLines.computeIfAbsent(sectionTarget, ignored -> new ArrayList<>()).add(renderedLine);
        }
        List<EmakiLoreSectionContribution> sections = new ArrayList<>();
        statSectionLines.entrySet().stream()
                .sorted(Map.Entry.<SectionTarget, List<String>>comparingByKey())
                .forEach(entry -> addSection(sections, entry.getKey().sectionId(), entry.getKey().order(), entry.getValue()));
        return sections;
    }

    private SectionTarget resolveStatSection(StrengthenRecipe.StatLineDefinition definition) {
        if (definition != null && Texts.isNotBlank(definition.sectionId())) {
            int order = definition.sectionOrder() > 0
                    ? definition.sectionOrder()
                    : Texts.lower(definition.sectionId()).contains("primary") ? STATS_PRIMARY_ORDER : STATS_SECONDARY_ORDER;
            return new SectionTarget(definition.sectionId(), order);
        }
        return new SectionTarget(
                "strengthen.stats.secondary",
                STATS_SECONDARY_ORDER
        );
    }

    private Map<String, Object> buildVariables(StrengthenRecipe recipe,
            StrengthenState state,
            Map<String, Double> stats) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (stats != null) {
            variables.putAll(stats);
        }
        variables.put("star", state.currentStar());
        variables.put("temper", state.temperLevel());
        variables.put("max_temper", recipe.limits().maxTemper());
        variables.put("temper_color", state.temperLevel() > 0 ? "<red>" : "<green>");
        return variables;
    }

    private String renderStatLine(StrengthenRecipe.StatLineDefinition definition,
            String statId,
            double value,
            Map<String, Object> variables) {
        Map<String, Object> renderVariables = new LinkedHashMap<>(variables);
        renderVariables.put("id", statId);
        renderVariables.put("sign", value >= 0D ? "+" : "-");
        renderVariables.put("value", Numbers.formatNumber(Math.abs(value), "0.##"));
        if (definition == null || Texts.isBlank(definition.template())) {
            return "<gray>" + humanize(statId) + ": <gold>"
                    + renderVariables.get("sign")
                    + renderVariables.get("value")
                    + "</gold></gray>";
        }
        return renderTemplate(definition.template(), renderVariables);
    }

    private String renderTemplate(String template, Map<String, Object> variables) {
        return ExpressionEngine.evaluateStringConfig(template, variables == null ? Map.of() : variables);
    }

    private String humanize(String statId) {
        String text = Texts.toStringSafe(statId).replace('_', ' ');
        return text.isBlank() ? "属性" : text;
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

    private EmakiStructuredPresentation resolveConfiguredPresentation(Object raw, Map<String, ?> variables) {
        StructuredPresentationValidator.ValidationResult validation = structuredValidator.sanitize(
                structuredResolver.fromConfig(raw, variables, NAMESPACE_ID)
        );
        return validation.presentation();
    }

    private boolean hasExplicitBaseName(EmakiStructuredPresentation presentation) {
        return presentation != null
                && presentation.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE
                && Texts.isNotBlank(presentation.baseNameTemplate());
    }

    private record SectionTarget(String sectionId, int order) implements Comparable<SectionTarget> {

        private SectionTarget {
            sectionId = Texts.isBlank(sectionId) ? "strengthen.stats.secondary" : sectionId;
            order = Math.max(0, order);
        }

        @Override
        public int compareTo(SectionTarget other) {
            if (other == null) {
                return -1;
            }
            int orderCompare = Integer.compare(order, other.order);
            return orderCompare != 0 ? orderCompare : sectionId.compareTo(other.sectionId);
        }
    }
}
