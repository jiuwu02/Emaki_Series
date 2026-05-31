package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.BaseNamePolicy;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.assembly.EmakiNameContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiLoreSectionContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.assembly.EmakiStructuredPresentation;
import emaki.jiuwu.craft.corelib.assembly.NamePosition;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
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
    private static final int OBTAIN_SECTION_ORDER = 100;

    private final EmakiGemPlugin plugin;
    private final GemLoreBuilder loreBuilder;

    public GemSnapshotBuilder(EmakiGemPlugin plugin) {
        this.plugin = plugin;
        this.loreBuilder = new GemLoreBuilder();
    }

    public EmakiItemLayerSnapshot build(GemItemDefinition itemDefinition, GemState state) {
        if (itemDefinition == null || state == null) {
            return new EmakiItemLayerSnapshot(NAMESPACE_ID, 1, Map.of(), List.of(), null);
        }
        List<EmakiStatContribution> stats = new ArrayList<>();
        int sequence = 0;

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
        GemResonanceService resonanceService = plugin.resonanceService();
        if (resonanceService != null) {
            List<GemResonanceService.GemEntry> inlaidGems = collectInlaidGemsWithLevels(state);
            List<GemResonanceDefinition> activeResonances = resonanceService.evaluateWithLevels(inlaidGems);
            for (GemResonanceDefinition resonance : activeResonances) {
                ResonanceEffects effects = resonance.effects();
                if (effects == null) {
                    continue;
                }
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
                parseObtainPresentation(itemDefinition, state)
        );
    }

    private EmakiStructuredPresentation parseObtainPresentation(GemItemDefinition itemDefinition, GemState state) {
        if (itemDefinition == null || itemDefinition.obtainConfig().emptyConfig()) {
            return null;
        }
        List<EmakiNameContribution> names = new ArrayList<>();
        List<EmakiLoreSectionContribution> sections = new ArrayList<>();
        Map<String, Object> placeholders = loreBuilder.buildItemPlaceholders(itemDefinition, state);
        int loreSequence = 0;
        String baseNameTemplate = appendNameContributions(names, itemDefinition.obtainConfig().nameActions(), placeholders);
        List<String> lines = loreBuilder.extractSafeLoreLines(itemDefinition.obtainConfig().loreActions(), placeholders);
        if (!lines.isEmpty()) {
            sections.add(new EmakiLoreSectionContribution(
                    "gem.obtain." + loreSequence,
                    OBTAIN_SECTION_ORDER + loreSequence,
                    lines,
                    NAMESPACE_ID
            ));
        }
        EmakiStructuredPresentation presentation = new EmakiStructuredPresentation(
                Texts.isBlank(baseNameTemplate) ? BaseNamePolicy.SOURCE_EFFECTIVE_NAME : BaseNamePolicy.EXPLICIT_TEMPLATE,
                baseNameTemplate,
                names,
                sections
        );
        return presentation.isEmpty() ? null : presentation;
    }

    private String appendNameContributions(List<EmakiNameContribution> names,
            Object nameActions,
            Map<String, ?> placeholders) {
        int sequence = 0;
        String baseNameTemplate = "";
        for (Object rawAction : ConfigNodes.asObjectList(nameActions)) {
            Object plain = ConfigNodes.toPlainData(rawAction);
            if (!(plain instanceof Map<?, ?> actionMap)) {
                continue;
            }
            String action = Texts.lower(ConfigNodes.string(actionMap, "action", ""));
            Object rawValue = ConfigNodes.get(actionMap, "value");
            String value = Texts.formatTemplate(Texts.toStringSafe(rawValue), placeholders == null ? Map.of() : placeholders);
            if (Texts.isBlank(value)) {
                continue;
            }
            if ("replace".equals(action)) {
                baseNameTemplate = value;
            } else if ("prepend_prefix".equals(action)) {
                names.add(new EmakiNameContribution("gem.obtain." + sequence, NamePosition.PREFIX, sequence, value, NAMESPACE_ID));
                sequence++;
            } else if ("append_suffix".equals(action)) {
                names.add(new EmakiNameContribution("gem.obtain." + sequence, NamePosition.POSTFIX, sequence, value, NAMESPACE_ID));
                sequence++;
            }
        }
        return baseNameTemplate;
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

    private List<GemResonanceService.GemEntry> collectInlaidGemsWithLevels(GemState state) {
        List<GemResonanceService.GemEntry> gems = new ArrayList<>();
        if (state == null) {
            return gems;
        }
        for (GemItemInstance instance : state.socketAssignments().values()) {
            if (instance == null) {
                continue;
            }
            GemDefinition definition = plugin.gemLoader().get(instance.gemId());
            if (definition != null) {
                gems.add(new GemResonanceService.GemEntry(definition, instance.level()));
            }
        }
        return gems;
    }
}
