package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.assembly.EmakiLoreSectionContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemResonanceDefinition;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.model.ResonanceEffects;

public final class GemSnapshotBuilder {

    private static final String NAMESPACE_ID = "gem";
    private static final int OVERVIEW_SECTION_ORDER = 100;
    private static final int STATUS_SECTION_ORDER = 400;

    private final EmakiGemPlugin plugin;
    private final GemLoreBuilder loreBuilder;

    public GemSnapshotBuilder(EmakiGemPlugin plugin) {
        this.plugin = plugin;
        this.loreBuilder = new GemLoreBuilder(plugin);
    }

    public EmakiItemLayerSnapshot build(GemItemDefinition itemDefinition, GemState state) {
        if (itemDefinition == null || state == null) {
            return new EmakiItemLayerSnapshot(NAMESPACE_ID, 1, Map.of(), List.of(), null);
        }
        List<EmakiStatContribution> stats = new ArrayList<>();
        List<EmakiLoreSectionContribution> loreSections = new ArrayList<>();
        int sequence = 0;

        addSection(
                loreSections,
                "gem.overview",
                OVERVIEW_SECTION_ORDER,
                loreBuilder.buildOverviewLines(itemDefinition, state, List.of())
        );

        for (var entry : state.socketAssignments().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            GemItemInstance instance = entry.getValue();
            GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
            if (instance == null || definition == null) {
                continue;
            }
            for (Map.Entry<String, Double> statEntry : definition.statsForLevel(instance.level()).entrySet()) {
                stats.add(new EmakiStatContribution(
                        statEntry.getKey(),
                        statEntry.getValue(),
                        definition.id(),
                        sequence++
                ));
            }
        }
        addSection(
                loreSections,
                "gem.status",
                STATUS_SECTION_ORDER,
                loreBuilder.buildSlotStatusLines(itemDefinition, state)
        );

        // Resonance evaluation
        // Note: resonance name_actions/lore_actions are now applied via ItemOperationLedger after item rebuild.
        // Only stats are still contributed to the layer snapshot here.
        GemResonanceService resonanceService = plugin.resonanceService();
        if (resonanceService != null) {
            List<GemDefinition> inlaidGems = collectInlaidGems(state);
            List<GemResonanceDefinition> activeResonances = resonanceService.evaluate(inlaidGems);
            for (GemResonanceDefinition resonance : activeResonances) {
                ResonanceEffects effects = resonance.effects();
                if (effects == null) {
                    continue;
                }
                // Add resonance stats
                for (Map.Entry<String, Double> statEntry : effects.stats().entrySet()) {
                    stats.add(new EmakiStatContribution(
                            statEntry.getKey(),
                            statEntry.getValue(),
                            "resonance:" + resonance.id(),
                            sequence++
                    ));
                }
            }
        }

        return new EmakiItemLayerSnapshot(
                NAMESPACE_ID,
                1,
                state.toAuditMap(),
                stats,
                null
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

    private void addSection(List<EmakiLoreSectionContribution> sections,
            String sectionId,
            int order,
            List<String> lines) {
        loreBuilder.addSection(sections, sectionId, order, lines);
    }

    private List<GemDefinition> collectInlaidGems(GemState state) {
        List<GemDefinition> gems = new ArrayList<>();
        if (state == null) {
            return gems;
        }
        for (GemItemInstance instance : state.socketAssignments().values()) {
            if (instance == null) {
                continue;
            }
            GemDefinition definition = plugin.gemLoader().get(instance.gemId());
            if (definition != null) {
                gems.add(definition);
            }
        }
        return gems;
    }
}
