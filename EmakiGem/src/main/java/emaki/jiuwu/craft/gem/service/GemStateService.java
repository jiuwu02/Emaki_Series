package emaki.jiuwu.craft.gem.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyService;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemState;

public final class GemStateService {

    public record RelationshipCheck(boolean allowed, String messageKey, Map<String, Object> placeholders) {

        public RelationshipCheck {
            messageKey = messageKey == null ? "" : messageKey;
            placeholders = placeholders == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(placeholders));
        }

        public static RelationshipCheck pass() {
            return new RelationshipCheck(true, "", Map.of());
        }

        public static RelationshipCheck denied(String messageKey, Map<String, Object> placeholders) {
            return new RelationshipCheck(false, messageKey, placeholders);
        }
    }

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

    public ItemStack applyInitialState(ItemStack original, GemItemDefinition itemDefinition) {
        return applyInitialState(null, original, itemDefinition);
    }

    public ItemStack applyInitialState(Player player, ItemStack original, GemItemDefinition itemDefinition) {
        if (original == null || itemDefinition == null || hasStoredLayer(original)) {
            return original;
        }
        GemState state = GemState.empty(itemDefinition.id()).withOpenedSlots(itemDefinition.defaultOpenedSlotIndexes());
        ItemStack rebuilt = applyState(original, itemDefinition, state);
        if (rebuilt != null && player != null && plugin.actionCoordinator() != null && !itemDefinition.obtainConfig().actions().isEmpty()) {
            plugin.actionCoordinator().execute(player, "gem_item_obtain", itemDefinition.obtainConfig().actions(), itemPlaceholders(itemDefinition, state));
        }
        return rebuilt == null ? original : rebuilt;
    }

    private Map<String, Object> itemPlaceholders(GemItemDefinition itemDefinition, GemState state) {
        Map<String, Object> placeholders = new LinkedHashMap<>();
        placeholders.put("item_definition_id", itemDefinition.id());
        placeholders.put("opened_slots", state.openedSlotIndexes().size());
        placeholders.put("inlaid_slots", state.socketAssignments().size());
        placeholders.put("total_slots", itemDefinition.slots().size());
        return placeholders;
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
            pdcAttributeWriter.apply(rebuilt, attributes);
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

    public RelationshipCheck validateInlayRelationships(GemState state, GemDefinition candidate) {
        if (state == null || candidate == null) {
            return RelationshipCheck.pass();
        }
        for (String dependencyId : candidate.dependencies()) {
            if (countAssignmentsByGemId(state, dependencyId) > 0) {
                continue;
            }
            return RelationshipCheck.denied("command.inlay.dependency_missing", Map.of(
                    "gem", candidate.displayName(),
                    "gem_id", candidate.id(),
                    "required_gem", gemDisplayName(dependencyId),
                    "required_gem_id", dependencyId
            ));
        }
        for (GemItemInstance existingInstance : state.socketAssignments().values()) {
            if (existingInstance == null) {
                continue;
            }
            String existingGemId = existingInstance.gemId();
            GemDefinition existingDefinition = plugin.gemLoader().get(existingGemId);
            boolean conflicts = candidate.conflicts().contains(existingGemId)
                    || existingDefinition != null && existingDefinition.conflicts().contains(candidate.id());
            if (!conflicts) {
                continue;
            }
            return RelationshipCheck.denied("command.inlay.gem_conflict", Map.of(
                    "gem", candidate.displayName(),
                    "gem_id", candidate.id(),
                    "conflict_gem", existingDefinition == null
                            ? existingGemId
                            : existingDefinition.displayNameForLevel(existingInstance.level()),
                    "conflict_gem_id", existingGemId
            ));
        }
        return RelationshipCheck.pass();
    }

    public RelationshipCheck validateExtractionRelationships(GemState state, int slotIndex) {
        if (state == null) {
            return RelationshipCheck.pass();
        }
        GemItemInstance removedInstance = state.assignment(slotIndex);
        if (removedInstance == null || countAssignmentsByGemId(state, removedInstance.gemId()) > 1) {
            return RelationshipCheck.pass();
        }
        for (Map.Entry<Integer, GemItemInstance> entry : state.socketAssignments().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            if (entry.getKey() == slotIndex || entry.getValue() == null) {
                continue;
            }
            GemItemInstance dependentInstance = entry.getValue();
            GemDefinition dependentDefinition = plugin.gemLoader().get(dependentInstance.gemId());
            if (dependentDefinition == null || !dependentDefinition.dependencies().contains(removedInstance.gemId())) {
                continue;
            }
            GemDefinition removedDefinition = plugin.gemLoader().get(removedInstance.gemId());
            return RelationshipCheck.denied("command.extract.dependency_required", Map.of(
                    "gem", removedDefinition == null
                            ? removedInstance.gemId()
                            : removedDefinition.displayNameForLevel(removedInstance.level()),
                    "gem_id", removedInstance.gemId(),
                    "dependent_gem", dependentDefinition.displayNameForLevel(dependentInstance.level()),
                    "dependent_gem_id", dependentDefinition.id()
            ));
        }
        return RelationshipCheck.pass();
    }

    private String gemDisplayName(String gemId) {
        GemDefinition definition = plugin.gemLoader().get(gemId);
        return definition == null ? gemId : definition.displayName();
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
