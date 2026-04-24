package emaki.jiuwu.craft.gem.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemState;

public final class GemStateService {

    private static final String NAMESPACE_ID = "gem";

    private final EmakiGemPlugin plugin;
    private final GemItemMatcher itemMatcher;
    private final GemSnapshotBuilder snapshotBuilder;
    private final GemPdcAttributeWriter pdcAttributeWriter;
    private final EmakiItemAssemblyService assemblyService;

    public GemStateService(EmakiGemPlugin plugin,
            GemItemMatcher itemMatcher,
            GemSnapshotBuilder snapshotBuilder,
            GemPdcAttributeWriter pdcAttributeWriter,
            EmakiItemAssemblyService assemblyService) {
        this.plugin = plugin;
        this.itemMatcher = itemMatcher;
        this.snapshotBuilder = snapshotBuilder;
        this.pdcAttributeWriter = pdcAttributeWriter;
        this.assemblyService = assemblyService;
    }

    public GemItemDefinition resolveItemDefinition(ItemStack itemStack) {
        return itemMatcher.matchEquipment(itemStack);
    }

    public GemState resolveState(ItemStack itemStack) {
        GemItemDefinition itemDefinition = resolveItemDefinition(itemStack);
        return itemDefinition == null ? null : resolveState(itemStack, itemDefinition);
    }

    public GemState resolveState(ItemStack itemStack, GemItemDefinition itemDefinition) {
        if (itemDefinition == null) {
            return null;
        }
        GemState current = readStoredState(itemStack);
        if (current == null || !itemDefinition.id().equals(current.itemDefinitionId())) {
            current = GemState.empty(itemDefinition.id()).withOpenedSlots(itemDefinition.defaultOpenedSlotIndexes());
        } else {
            current = current.withOpenedSlots(itemDefinition.defaultOpenedSlotIndexes());
        }
        return current;
    }

    public GemState readStoredState(ItemStack itemStack) {
        if (assemblyService == null || itemStack == null) {
            return null;
        }
        EmakiItemLayerSnapshot snapshot = assemblyService.readLayerSnapshot(itemStack, NAMESPACE_ID);
        return snapshot == null ? null : GemState.fromAuditMap(snapshot.audit());
    }

    public boolean hasStoredLayer(ItemStack itemStack) {
        if (assemblyService == null || itemStack == null) {
            return false;
        }
        return assemblyService.readLayerSnapshot(itemStack, NAMESPACE_ID) != null;
    }

    public ItemStack applyState(ItemStack original, GemItemDefinition itemDefinition, GemState state) {
        if (original == null || itemDefinition == null || state == null || assemblyService == null) {
            return null;
        }
        EmakiItemLayerSnapshot snapshot = snapshotBuilder.build(itemDefinition, state);
        ItemStack rebuilt = assemblyService.preview(new EmakiItemAssemblyRequest(null, original.getAmount(), original, Set.of(snapshot)));
        if (rebuilt == null) {
            return null;
        }
        pdcAttributeWriter.copyOtherSources(original, rebuilt);
        Map<String, Double> attributes = snapshotBuilder.aggregateAttributes(state);
        if (attributes.isEmpty() || !pdcAttributeWriter.available()) {
            pdcAttributeWriter.clear(rebuilt);
        } else {
            pdcAttributeWriter.apply(rebuilt, attributes, Map.of(
                    "item_definition_id", itemDefinition.id(),
                    "opened_slots", String.valueOf(state.openedSlotIndexes().size())
            ));
        }
        pdcAttributeWriter.applySkills(rebuilt, snapshotBuilder.aggregateSkillIds(state));
        return rebuilt;
    }

    public ItemStack clearGemLayer(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || assemblyService == null) {
            return null;
        }
        if (!assemblyService.isEmakiItem(itemStack) || assemblyService.readLayerSnapshot(itemStack, NAMESPACE_ID) == null) {
            return null;
        }
        ItemStack rebuilt = assemblyService.removeLayer(itemStack, NAMESPACE_ID);
        if (rebuilt == null) {
            return null;
        }
        rebuilt.setAmount(Math.max(1, itemStack.getAmount()));
        pdcAttributeWriter.copyOtherSources(itemStack, rebuilt);
        pdcAttributeWriter.clear(rebuilt);
        pdcAttributeWriter.applySkills(rebuilt, java.util.List.of());
        return rebuilt;
    }

    public int firstClosedSlot(GemItemDefinition itemDefinition, GemState state, SocketTypePredicate predicate) {
        if (itemDefinition == null || state == null) {
            return -1;
        }
        for (GemItemDefinition.SocketSlot slot : itemDefinition.slots()) {
            if (state.isOpened(slot.index())) {
                continue;
            }
            if (predicate == null || predicate.test(slot.type())) {
                return slot.index();
            }
        }
        return -1;
    }

    public Map<String, Integer> countAssignmentsByType(GemItemDefinition itemDefinition, GemState state) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (itemDefinition == null || state == null) {
            return counts;
        }
        for (var entry : state.socketAssignments().entrySet()) {
            var instance = entry.getValue();
            var definition = instance == null ? null : plugin.gemLoader().get(instance.gemId());
            if (definition == null) {
                continue;
            }
            counts.merge(definition.gemType(), 1, Integer::sum);
        }
        return counts;
    }

    public int countAssignmentsByGemId(GemState state, String gemId) {
        if (state == null) {
            return 0;
        }
        int count = 0;
        for (var entry : state.socketAssignments().values()) {
            if (entry != null && gemId != null && gemId.equalsIgnoreCase(entry.gemId())) {
                count++;
            }
        }
        return count;
    }

    @FunctionalInterface
    public interface SocketTypePredicate {

        boolean test(String socketType);
    }
}
