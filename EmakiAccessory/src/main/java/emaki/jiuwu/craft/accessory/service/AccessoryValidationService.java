package emaki.jiuwu.craft.accessory.service;

import java.util.List;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.config.AccessorySlotSourceConfig;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;

public final class AccessoryValidationService {

    private AccessoryValidationService() {
    }

    public static int returnInvalidFor(Player owner,
            PlayerAccessories accessories,
            AccessoryPageRegistry pageRegistry,
            AccessorySlotSourceConfig slotSources,
            Consumer<String> slotNotifier) {
        if (owner == null || accessories == null || pageRegistry == null) {
            return 0;
        }
        int returned = 0;
        for (String pageId : accessories.pageIds()) {
            for (String slotInstanceId : List.copyOf(accessories.slotKeys(pageId))) {
                if (!pageRegistry.declaresSlot(pageId, slotInstanceId)) {
                    continue;
                }
                ItemStack stored = accessories.itemAt(pageId, slotInstanceId);
                if (stored == null || stored.getType().isAir()) {
                    continue;
                }
                if (AccessorySlotDeclarations.matchesAny(slotInstanceId,
                        AccessorySlotDeclarations.read(stored, slotSources))) {
                    continue;
                }
                accessories.remove(pageId, slotInstanceId);
                InventoryItemUtil.addOrDrop(owner, stored.clone());
                if (slotNotifier != null) {
                    slotNotifier.accept(slotInstanceId);
                }
                returned++;
            }
        }
        return returned;
    }
}