package emaki.jiuwu.craft.accessory.gui;

import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.api.EmakiAttributeApi;
import emaki.jiuwu.craft.attribute.api.model.PdcAttributePayload;
import emaki.jiuwu.craft.corelib.api.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPayload;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPdcCodec;

final class AccessorySlotDeclaration {

    private AccessorySlotDeclaration() {
    }

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
