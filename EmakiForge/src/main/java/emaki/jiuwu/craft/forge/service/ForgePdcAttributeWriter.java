package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.ForgePdcKeys;
import emaki.jiuwu.craft.forge.integration.ForgeAttributeBridge;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.skills.api.pdc.EquipmentSkillPdcCodec;
import emaki.jiuwu.craft.skills.api.pdc.SkillPdcMutation;

final class ForgePdcAttributeWriter {

    private static final String SOURCE_ID = "forge";

    private final EmakiForgePlugin plugin;
    private final PdcService pdcService;

    ForgePdcAttributeWriter(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        this.pdcService = new PdcService(
                ForgePdcKeys.NAMESPACE,
                "pdc",
                plugin == null ? null : plugin.debugLogger()
        );
    }

    void apply(Recipe recipe,
            List<ForgeMaterialContribution> materials,
            double multiplier,
            QualitySettings.QualityTier qualityTier,
            ItemStack itemStack) {
        if (itemStack == null) {
            return;
        }
        ForgeAttributeBridge gateway = plugin.pdcAttributeGateway();
        Map<String, Double> attributes = new LinkedHashMap<>();
        Map<String, String> meta = new LinkedHashMap<>();
        List<String> skillIds = new ArrayList<>();
        if (materials != null) {
            for (ForgeMaterialContribution contribution : materials) {
                if (contribution == null || contribution.material() == null || contribution.amount() <= 0) {
                    continue;
                }
                skillIds.addAll(contribution.material().skillIds());
                for (Map.Entry<String, Double> entry : contribution.material().attributeContributions().entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        continue;
                    }
                    double value = entry.getValue() * contribution.amount() * multiplier;
                    if (Math.abs(value) <= 1.0E-9D) {
                        continue;
                    }
                    attributes.merge(entry.getKey(), value, Double::sum);
                }
            }
        }
        observeSkillMutation(itemStack, EquipmentSkillPdcCodec.write(itemStack, skillIds));
        writeForgeVariables(recipe, multiplier, qualityTier, itemStack);
        writeMaterialAudit(materials, itemStack);
        if (gateway == null || !gateway.available()) {
            return;
        }
        if (attributes.isEmpty()) {
            gateway.clear(itemStack, SOURCE_ID);
            return;
        }
        if (recipe != null) {
            meta.put("recipe_id", recipe.id());
        }
        if (qualityTier != null) {
            meta.put("quality", qualityTier.name());
        }
        gateway.write(itemStack, SOURCE_ID, attributes, meta);
    }

    private void writeForgeVariables(Recipe recipe,
            double multiplier,
            QualitySettings.QualityTier qualityTier,
            ItemStack itemStack) {
        if (pdcService == null) {
            return;
        }
        PdcPartition partition = pdcService.partition(ForgePdcKeys.FORGE_PARTITION);
        if (recipe != null && Texts.isNotBlank(recipe.id())) {
            pdcService.set(itemStack, partition, ForgePdcKeys.FORGE_RECIPE_ID,
                    PersistentDataType.STRING, recipe.id());
        }
        if (qualityTier != null && Texts.isNotBlank(qualityTier.name())) {
            pdcService.set(itemStack, partition, ForgePdcKeys.QUALITY_ID,
                    PersistentDataType.STRING, qualityTier.name());
            pdcService.set(itemStack, partition, ForgePdcKeys.QUALITY_DISPLAY,
                    PersistentDataType.STRING, qualityTier.name());
        }
        if (Double.isFinite(multiplier)) {
            pdcService.set(itemStack, partition, ForgePdcKeys.QUALITY_MULTIPLIER,
                    PersistentDataType.STRING, Numbers.formatNumber(multiplier, "0.##"));
        }

        pdcService.purgeLegacyKeys(itemStack);
    }

    private void writeMaterialAudit(List<ForgeMaterialContribution> materials, ItemStack itemStack) {
        if (pdcService == null || itemStack == null) {
            return;
        }
        PdcPartition partition = pdcService.partition(ForgePdcKeys.FORGE_PARTITION);
        List<String> materialIds = new ArrayList<>();
        List<String> countKeys = new ArrayList<>();
        List<String> auditIds = new ArrayList<>();
        List<String> matchedSources = new ArrayList<>();
        List<String> matcherDigests = new ArrayList<>();
        List<Map<String, Object>> allocations = new ArrayList<>();
        int consumed = 0;
        if (materials != null) {
            for (ForgeMaterialContribution contribution : materials) {
                if (contribution == null || contribution.material() == null || contribution.amount() <= 0) {
                    continue;
                }
                if (contribution.material().materialIdDeclared()) {
                    materialIds.add(contribution.material().materialId());
                }
                if (contribution.material().countKeyDeclared()) {
                    countKeys.add(contribution.material().countKey());
                }
                if (contribution.material().auditIdDeclared()) {
                    auditIds.add(contribution.material().auditId());
                }
                matchedSources.add(contribution.source() == null ? "" : emaki.jiuwu.craft.corelib.item.ItemSourceUtil.toShorthand(contribution.source()));
                matcherDigests.add(contribution.material().matcherKey());
                consumed += contribution.amountConsumed();
                allocations.add(contribution.toAuditMap());
            }
        }
        writeOrRemove(itemStack, partition, ForgePdcKeys.MATERIAL_ID, materialIds);
        writeOrRemove(itemStack, partition, ForgePdcKeys.COUNT_KEY, countKeys);
        writeOrRemove(itemStack, partition, ForgePdcKeys.AUDIT_ID, auditIds);
        pdcService.set(itemStack, partition, ForgePdcKeys.MATCHED_SOURCE, PersistentDataType.STRING, String.join(",", matchedSources));
        pdcService.set(itemStack, partition, ForgePdcKeys.AMOUNT_CONSUMED, PersistentDataType.INTEGER, consumed);
        pdcService.set(itemStack, partition, ForgePdcKeys.MATCHER_DIGEST, PersistentDataType.STRING, String.join(",", matcherDigests));
        pdcService.set(itemStack, partition, ForgePdcKeys.ALLOCATION, PersistentDataType.STRING, SignatureUtil.stableSignature(allocations));
    }

    private void writeOrRemove(ItemStack itemStack, PdcPartition partition, String field, List<String> values) {
        if (values == null || values.isEmpty()) {
            pdcService.remove(itemStack, partition, field);
            return;
        }
        pdcService.set(itemStack, partition, field, PersistentDataType.STRING, String.join(",", values));
    }

    private void observeSkillMutation(ItemStack itemStack, SkillPdcMutation mutation) {
        if (plugin == null
                || plugin.debugLogger() == null
                || mutation == null
                || !plugin.debugLogger().shouldLog("pdc", (UUID) null)) {
            return;
        }
        plugin.debugLogger().log("pdc", (UUID) null, "pdc.skill_payload", Map.of(
                "operation", mutation.operation(),
                "item", itemStack == null ? "null" : itemStack.getType(),
                "amount", itemStack == null ? 0 : itemStack.getAmount(),
                "before", mutation.before().values(),
                "after", mutation.after().values(),
                "committed", mutation.committed(),
                "reason", mutation.reason()
        ));
    }
}
