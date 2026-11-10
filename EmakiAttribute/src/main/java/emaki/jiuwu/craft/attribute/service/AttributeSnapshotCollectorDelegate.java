package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import emaki.jiuwu.craft.attribute.api.extension.AttributeContribution;
import emaki.jiuwu.craft.attribute.api.extension.AttributeContributionProvider;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.AttributeValueKind;
import emaki.jiuwu.craft.attribute.model.DamageStageDefinition;
import emaki.jiuwu.craft.attribute.model.DamageStageKind;
import emaki.jiuwu.craft.attribute.model.DamageStageMode;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class AttributeSnapshotCollectorDelegate {

    private final AttributeSnapshotCollector ownerRef;

    AttributeSnapshotCollectorDelegate(AttributeSnapshotCollector ownerRef) {
        this.ownerRef = ownerRef;
    }

    void addEquipmentSignature(List<String> signatureParts, String slotName, String itemSignature, PdcAttributeService.PdcAttributeViews views, boolean collectSignatures, String rejectingGateId) {
        if (!collectSignatures) {
            return;
        }
        String gatePart = "condition_gate=" + Texts.toStringSafe(rejectingGateId);
        if (views == null) {
            signatureParts.add(slotName + ":" + SignatureUtil.combine(
                    itemSignature,
                    gatePart
            ));
            return;
        }
        signatureParts.add(slotName + ":" + SignatureUtil.combine(
                itemSignature,
                views.filtered().sourceSignature(),
                "declared_slots=" + String.join(",", views.declaredSlots()),
                "item_slot_matched=" + views.itemSlotMatched(),
                gatePart
        ));
    }

}
