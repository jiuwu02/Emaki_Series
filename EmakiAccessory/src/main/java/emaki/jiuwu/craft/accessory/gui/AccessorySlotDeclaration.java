package emaki.jiuwu.craft.accessory.gui;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.model.PdcAttributePayload;
import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPayload;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPdcCodec;

/**
 * Reads the accessory slot an item declares it belongs in.
 *
 * <p>The {@code active_slot} key is shared between EmakiAttribute and EmakiSkills, so a single item-side
 * declaration governs both. This helper reads whichever of the two is installed, preferring the
 * attribute payload because attribute-only accessories are the common case.
 *
 * <p>Both reads degrade to an empty answer when the source plugin is absent, which is what makes the
 * container work standalone: with neither installed nothing declares a restriction, so every accessory
 * fits every cell.
 */
final class AccessorySlotDeclaration {

    private AccessorySlotDeclaration() {
    }

    /**
     * Reads an item's declared slot.
     *
     * @param item the item to inspect
     * @return the declared slot, or an empty string when the item declares none
     */
    static String read(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "";
        }
        String fromAttributes = fromAttributePayloads(item);
        if (Texts.isNotBlank(fromAttributes)) {
            return fromAttributes;
        }
        return fromSkillPayload(item);
    }

    /**
     * Reads the declaration carried by attribute payloads.
     *
     * <p>An item may hold several attribute sources. The first non-blank declaration wins, matching the
     * attribute side's own gate, which treats any declared-but-unmatched source as a reason to drop the
     * item's contribution rather than averaging the sources somehow.
     */
    private static String fromAttributePayloads(ItemStack item) {
        if (!EmakiAttributeApi.status().usable()) {
            return "";
        }
        Map<String, PdcAttributePayload> payloads = EmakiAttributeApi.extensions().pdc().readAll(item);
        for (PdcAttributePayload payload : payloads.values()) {
            if (payload == null) {
                continue;
            }
            String declared = payload.meta().get(EquipmentSlotMatcher.ACTIVE_SLOT_META_KEY);
            if (Texts.isNotBlank(declared)) {
                return Texts.normalizeId(declared);
            }
        }
        return "";
    }

    private static String fromSkillPayload(ItemStack item) {
        if (!EquipmentSkillPdcCodec.hasPayload(item)) {
            return "";
        }
        EquipmentSkillPayload payload = EquipmentSkillPdcCodec.read(item);
        if (payload == null) {
            return "";
        }
        return Texts.normalizeId(payload.activeSlot());
    }
}
