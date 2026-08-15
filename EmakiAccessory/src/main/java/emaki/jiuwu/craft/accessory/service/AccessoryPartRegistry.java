package emaki.jiuwu.craft.accessory.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.accessory.model.AccessoryPart;
import emaki.jiuwu.craft.accessory.model.AccessorySlot;
import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class AccessoryPartRegistry {

    private static final AccessoryPartRegistry EMPTY =
            new AccessoryPartRegistry(Map.of(), Map.of(), List.of());

    private final Map<String, AccessoryPart> parts;
    private final Map<String, AccessorySlot> slots;
    private final List<String> slotInstanceIds;

    private AccessoryPartRegistry(Map<String, AccessoryPart> parts,
            Map<String, AccessorySlot> slots,
            List<String> slotInstanceIds) {
        this.parts = parts;
        this.slots = slots;
        this.slotInstanceIds = slotInstanceIds;
    }

    public static AccessoryPartRegistry empty() {
        return EMPTY;
    }

    public static AccessoryPartRegistry of(List<AccessoryPart> parts, Map<String, String> rejected) {
        Map<String, AccessoryPart> acceptedParts = new LinkedHashMap<>();
        Map<String, AccessorySlot> acceptedSlots = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();
        if (parts != null) {
            for (AccessoryPart part : parts) {
                if (part == null || Texts.isBlank(part.partId())) {
                    continue;
                }
                if (acceptedParts.containsKey(part.partId())) {
                    record(rejected, part.partId(), part.partId());
                    continue;
                }
                List<AccessorySlot> expanded = new ArrayList<>(part.count());
                String collision = null;
                for (int index = 1; index <= part.count(); index++) {
                    AccessorySlot slot = AccessorySlot.of(part, index);
                    if (acceptedSlots.containsKey(slot.slotInstanceId())) {
                        collision = slot.slotInstanceId();
                        break;
                    }
                    expanded.add(slot);
                }
                if (collision != null) {
                    record(rejected, part.partId(), collision);
                    continue;
                }
                acceptedParts.put(part.partId(), part);
                for (AccessorySlot slot : expanded) {
                    acceptedSlots.put(slot.slotInstanceId(), slot);
                    order.add(slot.slotInstanceId());
                }
            }
        }
        return new AccessoryPartRegistry(
                Map.copyOf(acceptedParts),
                Map.copyOf(acceptedSlots),
                List.copyOf(order)
        );
    }

    private static void record(Map<String, String> rejected, String partId, String collidingId) {
        if (rejected != null) {
            rejected.put(partId, collidingId);
        }
    }

    public Map<String, AccessoryPart> parts() {
        return parts;
    }

    public Map<String, AccessorySlot> slots() {
        return slots;
    }

    public List<String> slotInstanceIds() {
        return slotInstanceIds;
    }

    public int slotCount() {
        return slotInstanceIds.size();
    }

    public AccessorySlot slot(String slotInstanceId) {
        return slots.get(Texts.normalizeId(slotInstanceId));
    }

    public boolean isConfigured(String slotInstanceId) {
        return slots.containsKey(Texts.normalizeId(slotInstanceId));
    }

    public boolean isOrphan(String slotInstanceId) {
        return !isConfigured(slotInstanceId);
    }

    public static boolean matchesAccessorySlot(String actualSlotInstanceId, String requiredSlot) {
        String required = Texts.normalizeId(requiredSlot);
        if (Texts.isBlank(required) || EquipmentSlotMatcher.SLOT_ALL.equals(required)) {
            return true;
        }
        String actual = Texts.normalizeId(actualSlotInstanceId);
        if (Texts.isBlank(actual)) {
            return false;
        }
        return required.equals(actual) || required.equals(AccessoryPart.partIdOf(actual));
    }
}
