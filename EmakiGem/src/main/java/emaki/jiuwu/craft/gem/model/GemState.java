package emaki.jiuwu.craft.gem.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public record GemState(String itemDefinitionId,
        Set<Integer> openedSlotIndexes,
        Map<Integer, GemItemInstance> socketAssignments,
        long updatedAt) {

    public GemState {
        itemDefinitionId = Texts.lower(itemDefinitionId);
        openedSlotIndexes = openedSlotIndexes == null ? Set.of() : copySlots(openedSlotIndexes);
        socketAssignments = socketAssignments == null ? Map.of() : copyAssignments(socketAssignments);
        updatedAt = updatedAt <= 0L ? System.currentTimeMillis() : updatedAt;
    }

    public static GemState empty(String itemDefinitionId) {
        return new GemState(itemDefinitionId, Set.of(), Map.of(), System.currentTimeMillis());
    }

    public boolean isOpened(int slotIndex) {
        return openedSlotIndexes.contains(slotIndex);
    }

    public GemItemInstance assignment(int slotIndex) {
        return socketAssignments.get(slotIndex);
    }

    public GemState withOpenedSlots(Collection<Integer> slotIndexes) {
        Set<Integer> nextOpened = new LinkedHashSet<>(openedSlotIndexes);
        boolean changed = false;
        if (slotIndexes != null) {
            for (Integer slotIndex : slotIndexes) {
                if (slotIndex != null && slotIndex >= 0 && nextOpened.add(slotIndex)) {
                    changed = true;
                }
            }
        }
        if (!changed) {
            return this;
        }
        return new GemState(itemDefinitionId, nextOpened, socketAssignments, System.currentTimeMillis());
    }

    public GemState withAssignment(int slotIndex, GemItemInstance itemInstance) {
        Map<Integer, GemItemInstance> nextAssignments = new LinkedHashMap<>(socketAssignments);
        if (itemInstance == null) {
            nextAssignments.remove(slotIndex);
        } else {
            nextAssignments.put(slotIndex, itemInstance);
        }
        Set<Integer> nextOpened = new LinkedHashSet<>(openedSlotIndexes);
        nextOpened.add(slotIndex);
        return new GemState(itemDefinitionId, nextOpened, nextAssignments, System.currentTimeMillis());
    }

    public Map<String, Object> toAuditMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("item_definition_id", itemDefinitionId);
        map.put("opened_slots", openedSlotIndexes.stream().sorted().toList());
        Map<String, Object> assignments = new LinkedHashMap<>();
        socketAssignments.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> assignments.put(String.valueOf(entry.getKey()), entry.getValue() == null ? null : entry.getValue().toMap()));
        map.put("socket_assignments", assignments);
        map.put("updated_at", updatedAt);
        return map;
    }

    public static GemState fromAuditMap(Map<String, Object> audit) {
        if (audit == null || audit.isEmpty()) {
            return null;
        }
        String itemDefinitionId = Texts.lower(audit.get("item_definition_id"));
        if (Texts.isBlank(itemDefinitionId)) {
            return null;
        }
        Set<Integer> openedSlots = new LinkedHashSet<>();
        for (Object value : ConfigNodes.asObjectList(audit.get("opened_slots"))) {
            Integer slot = Numbers.tryParseInt(value, null);
            if (slot != null && slot >= 0) {
                openedSlots.add(slot);
            }
        }
        Map<Integer, GemItemInstance> assignments = new LinkedHashMap<>();
        Object rawAssignments = ConfigNodes.toPlainData(audit.get("socket_assignments"));
        if (rawAssignments instanceof Map<?, ?> assignmentMap) {
            for (Map.Entry<?, ?> entry : assignmentMap.entrySet()) {
                Integer slot = Numbers.tryParseInt(entry.getKey(), null);
                Object plain = ConfigNodes.toPlainData(entry.getValue());
                if (slot == null || slot < 0 || !(plain instanceof Map<?, ?> valueMap)) {
                    continue;
                }
                GemItemInstance instance = GemItemInstance.fromMap(normalizeMap(valueMap));
                if (instance != null) {
                    assignments.put(slot, instance);
                }
            }
        }
        return new GemState(
                itemDefinitionId,
                openedSlots,
                assignments,
                Numbers.tryParseLong(audit.get("updated_at"), System.currentTimeMillis())
        );
    }

    private static Set<Integer> copySlots(Collection<Integer> slots) {
        Set<Integer> copy = new LinkedHashSet<>();
        for (Integer slot : slots) {
            if (slot != null && slot >= 0) {
                copy.add(slot);
            }
        }
        return Set.copyOf(copy);
    }

    private static Map<Integer, GemItemInstance> copyAssignments(Map<Integer, GemItemInstance> assignments) {
        Map<Integer, GemItemInstance> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, GemItemInstance> entry : assignments.entrySet()) {
            Integer slotIndex = entry.getKey();
            GemItemInstance instance = entry.getValue();
            if (slotIndex == null || slotIndex < 0 || instance == null) {
                continue;
            }
            copy.put(slotIndex, instance);
        }
        return Map.copyOf(copy);
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(String.valueOf(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
        }
        return normalized;
    }
}
