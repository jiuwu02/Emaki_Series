package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeAuditBuilder {

    Map<String, Object> buildAudit(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            QualitySettings.QualityTier qualityTier,
            double multiplier,
            long forgedAt) {
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("recipe_id", recipe == null ? "" : recipe.id());
        audit.put("schema", "forge_audit_v2");
        audit.put("schema_version", 2);
        audit.put("stage", "forge");
        audit.put("quality", qualityTier == null ? "" : qualityTier.name());
        audit.put("multiplier", multiplier);
        audit.put("forged_at", forgedAt);
        audit.put("materials_signature", buildMaterialsSignature(materials));
        if (recipe != null && recipe.configuredOutputSource() != null) {
            audit.put("output_item", ItemSourceUtil.toShorthand(recipe.configuredOutputSource()));
        }
        List<Map<String, Object>> materialMaps = new ArrayList<>();
        if (materials != null) {
            for (ForgeMaterialContribution material : materials) {
                if (material != null) {
                    materialMaps.add(material.toAuditMap());
                }
            }
        }
        audit.put("materials", materialMaps);
        return audit;
    }

    String buildMaterialsSignature(List<ForgeMaterialContribution> materials) {
        List<Map<String, Object>> signatureData = new ArrayList<>();
        if (materials != null) {
            for (ForgeMaterialContribution material : materials) {
                if (material != null && material.amount() > 0) {
                    signatureData.add(material.toSignatureData());
                }
            }
        }
        return SignatureUtil.stableSignature(signatureData);
    }
}
