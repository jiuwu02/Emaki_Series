package emaki.jiuwu.craft.accessory.service;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.model.AccessoryPart;
import emaki.jiuwu.craft.accessory.model.AccessorySlot;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class AccessoryDurabilityService {

    private AccessoryDurabilityService() {
    }

    public static List<String> deduct(PlayerAccessories accessories,
            AccessoryPageRegistry pageRegistry,
            AccessoryPartRegistry partRegistry,
            String pageId,
            int points) {
        if (accessories == null || pageRegistry == null || Texts.isBlank(pageId) || points <= 0) {
            return List.of();
        }
        List<String> brokenSlots = new ArrayList<>();
        for (String slotInstanceId : pageRegistry.slotsOf(pageId)) {
            if (!allowsDeduction(partRegistry, slotInstanceId)) {
                continue;
            }
            ItemStack stored = accessories.itemAt(pageId, slotInstanceId);
            if (stored == null || stored.getType().isAir()) {
                continue;
            }
            int maxDurability = AccessoryEffectGate.maxDurability(stored);
            if (maxDurability <= 0) {
                continue;
            }
            boolean intactBefore = AccessoryEffectGate.damage(stored) < maxDurability;
            ItemStack updated = stored.clone();
            if (AccessoryEffectGate.applyDamage(updated, points) <= 0) {
                continue;
            }
            accessories.put(pageId, slotInstanceId, updated);
            if (intactBefore && !AccessoryEffectGate.durabilityIntact(updated)) {
                brokenSlots.add(slotInstanceId);
            }
        }
        return List.copyOf(brokenSlots);
    }

    private static boolean allowsDeduction(AccessoryPartRegistry partRegistry, String slotInstanceId) {
        if (partRegistry == null) {
            return true;
        }
        AccessorySlot slot = partRegistry.slot(slotInstanceId);
        if (slot == null) {
            return true;
        }
        AccessoryPart part = partRegistry.parts().get(slot.partId());
        return part == null || part.durabilityDeduction();
    }
}