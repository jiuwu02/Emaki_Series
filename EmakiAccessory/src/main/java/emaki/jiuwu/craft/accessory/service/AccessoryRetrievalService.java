package emaki.jiuwu.craft.accessory.service;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class AccessoryRetrievalService {

    private AccessoryRetrievalService() {
    }

    public static int retrievePage(Player owner, PlayerAccessories accessories, String pageId) {
        String page = Texts.normalizeId(pageId);
        if (owner == null || accessories == null || Texts.isBlank(page)) {
            return 0;
        }
        List<String> keys = List.copyOf(accessories.slotKeys(page));
        int delivered = 0;
        for (String slotInstanceId : keys) {
            ItemStack stored = accessories.itemAt(page, slotInstanceId);
            if (stored == null || stored.getType().isAir()) {
                accessories.remove(page, slotInstanceId);
                continue;
            }
            if (!deliver(owner, stored)) {
                continue;
            }
            accessories.remove(page, slotInstanceId);
            delivered++;
        }
        return delivered;
    }

    private static boolean deliver(Player owner, ItemStack stored) {
        if (!owner.isOnline()) {
            return false;
        }
        InventoryItemUtil.addOrDrop(owner, stored.clone());
        return true;
    }
}
