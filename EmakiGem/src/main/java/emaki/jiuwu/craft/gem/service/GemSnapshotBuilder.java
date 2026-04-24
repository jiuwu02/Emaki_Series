package emaki.jiuwu.craft.gem.service;

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
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;

public final class GemSnapshotBuilder {

    private static final String NAMESPACE_ID = "gem";
    private static final int OVERVIEW_SECTION_ORDER = 100;
    private static final int STATUS_SECTION_ORDER = 400;

    private final EmakiGemPlugin plugin;
    private final GemLoreBuilder loreBuilder;
    private final StructuredPresentationTemplateResolver structuredResolver;
    private final StructuredPresentationValidator structuredValidator;

    public GemSnapshotBuilder(EmakiGemPlugin plugin) {
        this.plugin = plugin;
        this.loreBuilder = new GemLoreBuilder(plugin);
        this.structuredResolver = new StructuredPresentationTemplateResolver();
        this.structuredValidator = new StructuredPresentationValidator();
    }

    public EmakiItemLayerSnapshot build(GemItemDefinition itemDefinition, GemState state) {
        if (itemDefinition == null || state == null) {
            return new EmakiItemLayerSnapshot(NAMESPACE_ID, 1, Map.of(), List.of(), null);
        }
        List<EmakiStatContribution> stats = new ArrayList<>();
        List<EmakiNameContribution> nameContributions = new ArrayList<>();
        List<EmakiLoreSectionContribution> loreSections = new ArrayList<>();
        BaseNamePolicy baseNamePolicy = BaseNamePolicy.SOURCE_EFFECTIVE_NAME;
        String baseNameTemplate = "";
        int sequence = 0;

        Map<String, Object> itemPlaceholders = loreBuilder.buildItemPlaceholders(itemDefinition, state);
        EmakiStructuredPresentation itemPresentation = resolveStructuredPresentation(itemDefinition.structuredPresentation(), itemPlaceholders);
        if (hasExplicitBaseName(itemPresentation)) {
            baseNamePolicy = itemPresentation.baseNamePolicy();
            baseNameTemplate = itemPresentation.baseNameTemplate();
        }
        if (itemPresentation != null) {
            nameContributions.addAll(itemPresentation.nameContributions());
        }
        addSection(
                loreSections,
                "gem.overview",
                OVERVIEW_SECTION_ORDER,
                loreBuilder.buildOverviewLines(
                        itemDefinition,
                        state,
                        itemPresentation == null ? List.of() : loreBuilder.extractOverviewExtraLines(itemPresentation)
                )
        );
        if (itemPresentation != null) {
            loreSections.addAll(loreBuilder.extractAdditionalSections(itemPresentation, "gem.overview"));
        }

        for (var entry : state.socketAssignments().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            GemItemInstance instance = entry.getValue();
            GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
            if (instance == null || definition == null) {
                continue;
            }
            int slotIndex = entry.getKey();
            var slot = itemDefinition.slot(slotIndex);
            Map<String, Object> placeholders = new LinkedHashMap<>();
            placeholders.put("slot_index", slotIndex);
            placeholders.put("slot_type", slot == null ? "unknown" : slot.type());
            placeholders.put("slot_name", slot == null ? "" : slot.displayName());
            placeholders.putAll(plugin.itemFactory().gemPlaceholders(definition, instance.level(), null));
            for (Map.Entry<String, Double> statEntry : definition.statsForLevel(instance.level()).entrySet()) {
                stats.add(new EmakiStatContribution(
                        statEntry.getKey(),
                        statEntry.getValue(),
                        definition.id(),
                        sequence++
                ));
            }
            EmakiStructuredPresentation gemPresentation = resolveStructuredPresentation(
                    definition.structuredPresentationForLevel(instance.level()),
                    placeholders
            );
            if (hasExplicitBaseName(gemPresentation)) {
                baseNamePolicy = gemPresentation.baseNamePolicy();
                baseNameTemplate = gemPresentation.baseNameTemplate();
            }
            if (gemPresentation != null) {
                nameContributions.addAll(gemPresentation.nameContributions());
            }
            if (gemPresentation != null) {
                loreSections.addAll(gemPresentation.loreSections());
            }
        }
        addSection(
                loreSections,
                "gem.status",
                STATUS_SECTION_ORDER,
                loreBuilder.buildSlotStatusLines(itemDefinition, state)
        );

        StructuredPresentationValidator.ValidationResult validation = structuredValidator.sanitize(new EmakiStructuredPresentation(
                baseNamePolicy,
                baseNameTemplate,
                nameContributions,
                loreSections
        ));
        EmakiStructuredPresentation structuredPresentation = validation.presentation();

        return new EmakiItemLayerSnapshot(
                NAMESPACE_ID,
                1,
                state.toAuditMap(),
                stats,
                structuredPresentation == null || structuredPresentation.isEmpty() ? null : structuredPresentation
        );
    }

    public Map<String, Double> aggregateAttributes(EmakiItemLayerSnapshot snapshot) {
        Map<String, Double> aggregated = new LinkedHashMap<>();
        if (snapshot == null) {
            return aggregated;
        }
        for (EmakiStatContribution contribution : snapshot.stats()) {
            if (contribution == null) {
                continue;
            }
            aggregated.merge(contribution.statId(), contribution.amount(), Double::sum);
        }
        return Map.copyOf(aggregated);
    }

    public Map<String, Double> aggregateAttributes(GemState state) {
        Map<String, Double> aggregated = new LinkedHashMap<>();
        if (state == null) {
            return aggregated;
        }
        for (GemItemInstance instance : state.socketAssignments().values()) {
            if (instance == null) {
                continue;
            }
            GemDefinition definition = plugin.gemLoader().get(instance.gemId());
            if (definition == null) {
                continue;
            }
            definition.attributesForLevel(instance.level()).forEach((attributeId, amount) ->
                    aggregated.merge(attributeId, amount, Double::sum)
            );
        }
        return Map.copyOf(aggregated);
    }

    public List<String> aggregateSkillIds(GemState state) {
        List<String> aggregated = new ArrayList<>();
        if (state == null) {
            return aggregated;
        }
        for (GemItemInstance instance : state.socketAssignments().values()) {
            if (instance == null) {
                continue;
            }
            GemDefinition definition = plugin.gemLoader().get(instance.gemId());
            if (definition == null) {
                continue;
            }
            aggregated.addAll(definition.skillIdsForLevel(instance.level()));
        }
        return List.copyOf(aggregated);
    }

    private EmakiStructuredPresentation resolveStructuredPresentation(Object raw, Map<String, ?> placeholders) {
        StructuredPresentationValidator.ValidationResult validation = structuredValidator.sanitize(
                structuredResolver.fromConfig(raw, placeholders, NAMESPACE_ID)
        );
        return validation.presentation();
    }

    private boolean hasExplicitBaseName(EmakiStructuredPresentation presentation) {
        return presentation != null
                && presentation.baseNamePolicy() == BaseNamePolicy.EXPLICIT_TEMPLATE
                && Texts.isNotBlank(presentation.baseNameTemplate());
    }

    private void addSection(List<EmakiLoreSectionContribution> sections,
            String sectionId,
            int order,
            List<String> lines) {
        loreBuilder.addSection(sections, sectionId, order, lines);
    }
}
