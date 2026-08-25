package emaki.jiuwu.craft.accessory.service;

import java.util.Locale;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.api.EmakiItemApi;

public final class AccessoryUniqueService {

    private boolean enabled;

    public AccessoryUniqueService(boolean enabled) {
        this.enabled = enabled;
    }

    public void reconfigure(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public String identityOf(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }
        if (EmakiItemApi.status().usable()) {
            String definitionId = EmakiItemApi.catalog().identify(item).orElse("");
            if (Texts.isNotBlank(definitionId)) {
                return "emakiitem:" + definitionId;
            }
        }
        return "material:" + item.getType().name().toLowerCase(Locale.ROOT);
    }

    public String findConflict(PlayerAccessories accessories,
            String pageId,
            ItemStack candidate,
            String targetSlotId) {
        if (!enabled || accessories == null) {
            return "";
        }
        String identity = identityOf(candidate);
        if (Texts.isBlank(identity)) {
            return "";
        }
        String target = Texts.normalizeId(targetSlotId);
        for (Map.Entry<String, ItemStack> entry : accessories.items(pageId).entrySet()) {
            if (entry.getKey().equals(target)) {
                continue;
            }
            if (identity.equals(identityOf(entry.getValue()))) {
                return entry.getKey();
            }
        }
        return "";
    }
}
