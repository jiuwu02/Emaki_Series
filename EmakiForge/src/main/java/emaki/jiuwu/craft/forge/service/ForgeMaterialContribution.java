package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;

record ForgeMaterialContribution(ForgeMaterial material,
        int amount,
        int slot,
        String category,
        int sequence,
        ItemSource source) {

    ForgeMaterialContribution      {
        amount = Math.max(0, amount);
        category = Texts.toStringSafe(category);
        sequence = Math.max(0, sequence);
    }

    List<ForgeMaterial.QualityModifier> qualityModifiers() {
        return material == null ? List.of() : material.qualityModifiers();
    }

    Map<String, Object> toAuditMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("material_id", material == null ? "" : material.id());
        map.put("category", category);
        map.put("amount", amount);
        map.put("slot", slot);
        map.put("sequence", sequence);
        map.put("source", source == null ? "" : ItemSourceUtil.toShorthand(source));
        return map;
    }

    Map<String, Object> toSignatureData() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("material", material == null ? Map.of() : material.definitionSignatureData());
        map.put("amount", amount);
        map.put("slot", slot);
        map.put("category", category);
        map.put("sequence", sequence);
        return map;
    }
}
