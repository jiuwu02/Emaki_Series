package emaki.jiuwu.craft.item.trigger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;

public final class EquipmentSourceResolver {

    public record EquipmentSource(ItemStack itemStack, String slotName) {
    }

    private static final Map<EquipmentSlot, String> SLOT_NAMES = Map.of(
            EquipmentSlot.HAND, EquipmentSlotMatcher.SLOT_MAIN_HAND,
            EquipmentSlot.OFF_HAND, EquipmentSlotMatcher.SLOT_OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlotMatcher.SLOT_HELMET,
            EquipmentSlot.CHEST, EquipmentSlotMatcher.SLOT_CHESTPLATE,
            EquipmentSlot.LEGS, EquipmentSlotMatcher.SLOT_LEGGINGS,
            EquipmentSlot.FEET, EquipmentSlotMatcher.SLOT_BOOTS
    );

    private static final EquipmentSlot[] SCANNED_SLOTS = {
            EquipmentSlot.HAND,
            EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private EquipmentSourceResolver() {
    }

    public static List<EquipmentSource> resolve(Player player) {
        List<EquipmentSource> sources = new ArrayList<>(SCANNED_SLOTS.length);
        if (player == null) {
            return sources;
        }
        EntityEquipment equipment = player.getEquipment();
        if (equipment == null) {
            return sources;
        }
        for (EquipmentSlot slot : SCANNED_SLOTS) {
            ItemStack itemStack = equipment.getItem(slot);
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            sources.add(new EquipmentSource(itemStack, SLOT_NAMES.get(slot)));
        }
        return sources;
    }
}
