package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;

record ForgeMaterialContribution(ForgeMaterial material,
        int amount,
        int amountConsumed,
        int slot,
        String category,
        int sequence,
        ItemSourceRef source,
        List<Map<String, Object>> allocation,
        double qualityScore) {

    ForgeMaterialContribution(ForgeMaterial material, int amount, int slot, String category,
            int sequence, ItemSourceRef source) {
        this(material, amount, amount * unitAmount(material), slot, category, sequence, source,
                List.of(Map.of("slot", slot, "amount", amount * unitAmount(material))), 0D);
    }

    ForgeMaterialContribution(ForgeMaterial material, int amount, int amountConsumed, int slot,
            String category, int sequence, ItemSourceRef source, List<Map<String, Object>> allocation) {
        this(material, amount, amountConsumed, slot, category, sequence, source, allocation, 0D);
    }

    ForgeMaterialContribution {
        amount = Math.max(0, amount);
        amountConsumed = Math.max(0, amountConsumed);
        category = Texts.toStringSafe(category);
        sequence = Math.max(0, sequence);
        allocation = allocation == null ? List.of() : List.copyOf(allocation);
        qualityScore = Math.max(0D, qualityScore);
    }

    List<ForgeMaterial.QualityModifier> qualityModifiers() {
        return material == null ? List.of() : material.qualityModifiers();
    }

    Map<String, Object> toAuditMap() {
        String materialId = material == null ? "" : material.materialId();
        String legacyIdentity = material == null ? "" : material.legacySourceKey();
        Map<String, Object> map = new LinkedHashMap<>(ForgeAuditIdentityResolver.canonicalEntry(
                materialId,
                material == null ? "" : material.countKey(),
                material == null ? "" : material.auditId(),
                Texts.isBlank(legacyIdentity) ? materialId : legacyIdentity,
                category,
                amount,
                amountConsumed,
                slot,
                sequence,
                source == null ? "" : ItemSourceUtil.toShorthand(source),
                material == null ? "" : material.matcherKey()));
        map.put("allocation", allocation);
        if (material != null && (!material.materialIdDeclared() || !material.countKeyDeclared() || !material.auditIdDeclared())) {
            if (!material.materialIdDeclared()) {
                map.remove("material_id");
            }
            if (!material.countKeyDeclared()) {
                map.remove("count_key");
            }
            if (!material.auditIdDeclared()) {
                map.remove("audit_id");
            }
            map.put("legacy_identity", material.legacySourceKey());
            map.put("identity_diagnostic", "missing canonical identity field");
        }
        return map;
    }

    Map<String, Object> toSignatureData() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("material", material == null ? Map.of() : material.definitionSignatureData());
        map.put("amount", amount);
        map.put("amount_consumed", amountConsumed);
        map.put("slot", slot);
        map.put("category", category);
        map.put("sequence", sequence);
        map.put("matched_source", source == null ? "" : ItemSourceUtil.toShorthand(source));
        map.put("allocation", allocation);
        return map;
    }

    private static int unitAmount(ForgeMaterial material) {
        return material == null ? 1 : Math.max(1, material.amount());
    }
}
