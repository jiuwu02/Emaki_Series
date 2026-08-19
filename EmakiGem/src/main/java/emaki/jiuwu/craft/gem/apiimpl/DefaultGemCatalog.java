package emaki.jiuwu.craft.gem.apiimpl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.api.GemCatalog;
import emaki.jiuwu.craft.gem.api.model.GemDefinitionView;
import emaki.jiuwu.craft.gem.api.model.GemRelationshipCheck;
import emaki.jiuwu.craft.gem.api.model.GemRerollSessionView;
import emaki.jiuwu.craft.gem.api.model.GemResonanceView;
import emaki.jiuwu.craft.gem.api.model.GemStateView;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemResonanceDefinition;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.service.GemResonanceService;
import emaki.jiuwu.craft.gem.service.GemStateService;

public final class DefaultGemCatalog implements GemCatalog {

    private final EmakiGemPlugin plugin;

    public DefaultGemCatalog(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<GemStateView> readState(ItemStack equipment) {
        if (!ready() || empty(equipment)) {
            return Optional.empty();
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return Optional.empty();
        }
        GemState state = plugin.stateService().resolveState(equipment, itemDefinition);
        return Optional.of(GemApiMapper.stateView(itemDefinition, state));
    }

    @Override
    public boolean isGemItem(ItemStack itemStack) {
        return ready() && !empty(itemStack) && plugin.itemMatcher().readGemInstance(itemStack) != null;
    }

    @Override
    public boolean isOpenerItem(ItemStack itemStack) {
        return ready() && !empty(itemStack) && plugin.itemMatcher().isOpenerItem(itemStack);
    }

    @Override
    public Optional<GemDefinitionView> definition(String gemId) {
        if (!ready() || Texts.isBlank(gemId)) {
            return Optional.empty();
        }
        GemDefinition definition = plugin.gemLoader().get(Texts.lower(gemId));
        return definition == null ? Optional.empty() : Optional.of(GemApiMapper.definitionView(definition, definition.level()));
    }

    @Override
    public List<GemDefinitionView> definitions() {
        if (!ready()) {
            return List.of();
        }
        return plugin.gemLoader().all().values().stream()
                .filter(definition -> definition != null && Texts.isNotBlank(definition.id()))
                .sorted(Comparator.comparing(GemDefinition::id))
                .map(definition -> GemApiMapper.definitionView(definition, definition.level()))
                .toList();
    }

    @Override
    public EmakiResult<GemRelationshipCheck> canInlay(ItemStack equipment, ItemStack gemItem) {
        if (!ready()) {
            return EmakiResult.unavailable();
        }
        if (empty(equipment)) {
            return EmakiResult.invalidInput("gem.input.equipment_missing");
        }
        if (empty(gemItem)) {
            return EmakiResult.invalidInput("gem.input.gem_missing");
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return EmakiResult.notFound("gem.equipment_definition_not_found");
        }
        GemItemInstance instance = plugin.itemMatcher().readGemInstance(gemItem);
        if (instance == null) {
            return EmakiResult.notFound("gem.item_not_recognized");
        }
        GemDefinition gemDefinition = plugin.gemLoader().get(instance.gemId());
        if (gemDefinition == null) {
            return EmakiResult.notFound("gem.definition_not_found");
        }
        GemState state = plugin.stateService().resolveState(equipment, itemDefinition);
        GemRelationshipCheck compatibility = firstAvailableSlotCheck(itemDefinition, state, gemDefinition);
        if (!compatibility.allowed()) {
            return EmakiResult.success(compatibility);
        }
        GemStateService.RelationshipCheck runtimeCheck =
                plugin.stateService().validateInlayRelationships(state, gemDefinition);
        return EmakiResult.success(GemApiMapper.relationshipCheck(runtimeCheck));
    }

    @Override
    public EmakiResult<GemRelationshipCheck> canExtract(ItemStack equipment, int slotIndex) {
        if (!ready()) {
            return EmakiResult.unavailable();
        }
        if (empty(equipment)) {
            return EmakiResult.invalidInput("gem.input.equipment_missing");
        }
        if (slotIndex < 0) {
            return EmakiResult.invalidInput("gem.input.slot_invalid");
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return EmakiResult.notFound("gem.equipment_definition_not_found");
        }
        if (itemDefinition.slot(slotIndex) == null) {
            return EmakiResult.notFound("gem.slot_not_found");
        }
        GemState state = plugin.stateService().resolveState(equipment, itemDefinition);
        if (state.assignment(slotIndex) == null) {
            return EmakiResult.success(new GemRelationshipCheck(false,
                    "gem.extract.slot_empty", Map.of("slot", slotIndex)));
        }
        GemStateService.RelationshipCheck runtimeCheck =
                plugin.stateService().validateExtractionRelationships(state, slotIndex);
        return EmakiResult.success(GemApiMapper.relationshipCheck(runtimeCheck));
    }

    @Override
    public EmakiResult<GemResonanceView> resonance(ItemStack equipment) {
        if (!ready()) {
            return EmakiResult.unavailable();
        }
        if (empty(equipment)) {
            return EmakiResult.invalidInput("gem.input.equipment_missing");
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return EmakiResult.notFound("gem.equipment_definition_not_found");
        }
        GemResonanceService resonanceService = plugin.resonanceService();
        if (resonanceService == null) {
            return EmakiResult.unavailable();
        }
        GemState state = plugin.stateService().resolveState(equipment, itemDefinition);
        List<GemResonanceService.GemEntry> entries = new ArrayList<>();
        for (GemItemInstance instance : state.socketAssignments().values()) {
            GemDefinition definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
            if (definition != null) {
                entries.add(new GemResonanceService.GemEntry(definition, instance.level()));
            }
        }
        List<GemResonanceDefinition> active = resonanceService.evaluateWithLevels(entries);
        if (active.isEmpty()) {
            return EmakiResult.notFound("gem.resonance_not_active");
        }
        return EmakiResult.success(GemApiMapper.resonanceView(active.getFirst()));
    }

    @Override
    public Map<String, Double> aggregatedAttributes(ItemStack equipment) {
        if (!ready() || empty(equipment)) {
            return Map.of();
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return Map.of();
        }
        return plugin.snapshotBuilder().aggregateAttributes(plugin.stateService().resolveState(equipment, itemDefinition));
    }

    @Override
    public Set<String> aggregatedSkillIds(ItemStack equipment) {
        if (!ready() || empty(equipment)) {
            return Set.of();
        }
        GemItemDefinition itemDefinition = plugin.stateService().resolveItemDefinition(equipment);
        if (itemDefinition == null) {
            return Set.of();
        }
        return Set.copyOf(new LinkedHashSet<>(
                plugin.snapshotBuilder().aggregateSkillIds(plugin.stateService().resolveState(equipment, itemDefinition))));
    }

    @Override
    public Optional<GemRerollSessionView> rerollSession(java.util.UUID operatorId) {
        if (!ready() || operatorId == null || plugin.rerollSessionService() == null) {
            return Optional.empty();
        }
        return plugin.rerollSessionService().view(operatorId);
    }

    private GemRelationshipCheck firstAvailableSlotCheck(GemItemDefinition itemDefinition,
            GemState state,
            GemDefinition gemDefinition) {
        if (!itemDefinition.allowsGemType(gemDefinition.gemType())) {
            return new GemRelationshipCheck(false, "gem.inlay.gem_type_blocked",
                    Map.of("type", gemDefinition.gemType()));
        }
        if (itemDefinition.maxSameType() > 0
                && plugin.stateService().countAssignmentsByType(itemDefinition, state)
                        .getOrDefault(gemDefinition.gemType(), 0) >= itemDefinition.maxSameType()) {
            return new GemRelationshipCheck(false, "gem.inlay.max_same_type",
                    Map.of("type", gemDefinition.gemType()));
        }
        if (plugin.stateService().countAssignmentsByGemId(state, gemDefinition.id()) >= itemDefinition.maxSameId()) {
            return new GemRelationshipCheck(false, "gem.inlay.max_same_id",
                    Map.of("gem", gemDefinition.id()));
        }
        boolean compatible = itemDefinition.slots().stream()
                .filter(slot -> state.isOpened(slot.index()))
                .filter(slot -> state.assignment(slot.index()) == null)
                .anyMatch(slot -> gemDefinition.supportsSocketType(slot.type()));
        return compatible
                ? GemRelationshipCheck.pass()
                : new GemRelationshipCheck(false, "gem.inlay.no_compatible_slot", Map.of());
    }

    private boolean ready() {
        return plugin != null
                && plugin.isEnabled()
                && plugin.publicApiReady()
                && plugin.gemLoader() != null
                && plugin.itemMatcher() != null
                && plugin.stateService() != null
                && plugin.snapshotBuilder() != null;
    }

    private static boolean empty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir();
    }
}
