package emaki.jiuwu.craft.accessory.service;

import java.util.Locale;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.api.EmakiItemApi;

/**
 * Decides whether two accessories count as "the same item" for the {@code unique} restriction.
 *
 * <p>Multi-slot parts make this necessary. Wearing two identical rings adds their attributes twice but
 * unlocks their skill only once, because EmakiSkills de-duplicates unlocked skills by skill id. That
 * asymmetry is fixed by EmakiAttribute and EmakiSkills and cannot be removed from this side, so the
 * only clean answer is to forbid the duplicate outright.
 *
 * <p>Identity prefers the EmakiItem definition id, which is the project's canonical cross-plugin
 * identity, and falls back to {@link org.bukkit.Material} for plain items. NBT is deliberately not
 * compared: two rings that differ only in durability are still "the same ring" to a player, and a
 * stricter rule would be harder to predict than it is useful.
 */
public final class AccessoryUniqueService {

    private boolean enabled;

    /**
     * Creates the service.
     *
     * @param enabled whether the restriction is active
     */
    public AccessoryUniqueService(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Applies a configuration change.
     *
     * @param enabled whether the restriction is active
     */
    public void reconfigure(boolean enabled) {
        this.enabled = enabled;
    }

    /** {@return whether the restriction is active} */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Computes the identity key of one accessory.
     *
     * @param item the item
     * @return the identity key, or an empty string when the item carries none
     */
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

    /**
     * Finds a slot that already holds the same accessory.
     *
     * @param accessories     the player's current contents
     * @param candidate       the item about to be inserted
     * @param targetSlotId    the slot being filled, excluded from the scan
     * @return the conflicting slot instance id, or an empty string when there is no conflict
     */
    public String findConflict(PlayerAccessories accessories, ItemStack candidate, String targetSlotId) {
        if (!enabled || accessories == null) {
            return "";
        }
        String identity = identityOf(candidate);
        if (Texts.isBlank(identity)) {
            return "";
        }
        String target = Texts.normalizeId(targetSlotId);
        for (Map.Entry<String, ItemStack> entry : accessories.items().entrySet()) {
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
