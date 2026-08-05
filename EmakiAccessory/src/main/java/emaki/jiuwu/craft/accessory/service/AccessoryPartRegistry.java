package emaki.jiuwu.craft.accessory.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.accessory.model.AccessoryPart;
import emaki.jiuwu.craft.accessory.model.AccessorySlot;
import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Active accessory part configuration and the slot identity rules built on it.
 *
 * <p>Holds the expanded slot instances and answers "does this item's declared slot allow this cell".
 * That question cannot be delegated to {@link EquipmentSlotMatcher#matches(String, String)}: its only
 * widening rule is {@code hand -> main_hand | off_hand}, so a required {@code ring} would never match an
 * actual {@code ring_1}. Part-level widening is this module's own rule and lives here.
 *
 * <p>Replaced wholesale on reload rather than mutated in place, so a reader either sees the old
 * configuration or the new one and never a half-applied mix.
 */
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

    /** {@return an empty registry, used before the first load and after a failed one} */
    public static AccessoryPartRegistry empty() {
        return EMPTY;
    }

    /**
     * Expands parts into slot instances, rejecting collisions.
     *
     * <p>Instance ids must be globally unique because they are the key of player data. A collision is
     * resolved by rejecting the later part rather than merging: merging would silently give one part
     * control over another part's saved items.
     *
     * @param parts    the loaded parts in declaration order
     * @param rejected receives one entry per rejected part id and the id that collided
     * @return the built registry
     */
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

    /** {@return the accepted parts keyed by part id, in declaration order} */
    public Map<String, AccessoryPart> parts() {
        return parts;
    }

    /** {@return the accepted slot instances keyed by instance id, in part then index order} */
    public Map<String, AccessorySlot> slots() {
        return slots;
    }

    /** {@return the accepted slot instance ids, in part then index order} */
    public List<String> slotInstanceIds() {
        return slotInstanceIds;
    }

    /** {@return how many slot instances the configuration provides} */
    public int slotCount() {
        return slotInstanceIds.size();
    }

    /**
     * Looks up one slot instance.
     *
     * @param slotInstanceId the instance id
     * @return the slot, or {@code null} when the id is not part of the active configuration
     */
    public AccessorySlot slot(String slotInstanceId) {
        return slots.get(Texts.normalizeId(slotInstanceId));
    }

    /**
     * Tests whether a slot instance belongs to the active configuration.
     *
     * @param slotInstanceId the instance id
     * @return whether the slot is currently configured
     */
    public boolean isConfigured(String slotInstanceId) {
        return slots.containsKey(Texts.normalizeId(slotInstanceId));
    }

    /**
     * Tests whether a stored key has become an orphan.
     *
     * @param slotInstanceId the stored key
     * @return whether the key no longer maps to a configured slot
     */
    public boolean isOrphan(String slotInstanceId) {
        return !isConfigured(slotInstanceId);
    }

    /**
     * Tests whether an item declaring {@code requiredSlot} may occupy {@code actualSlotInstanceId}.
     *
     * <p>Three accepted forms: {@code all} or blank means any accessory cell, an exact instance id
     * pins the item to one cell, and a bare part id allows any cell of that part. The part-level form
     * is the widening rule CoreLib does not have.
     *
     * @param actualSlotInstanceId the cell being filled
     * @param requiredSlot         the slot declared by the item
     * @return whether the item is allowed in that cell
     */
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
