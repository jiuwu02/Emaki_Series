package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.integration.ForgeAttributeBridge;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.skills.protocol.EquipmentSkillPdcCodec;
import emaki.jiuwu.craft.skills.protocol.SkillPdcMutation;

final class ForgePdcAttributeWriter {

    private static final String SOURCE_ID = "forge";

    private final EmakiForgePlugin plugin;

    ForgePdcAttributeWriter(EmakiForgePlugin plugin) {
        this.plugin = plugin;
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
        java.util.List<String> skillIds = new java.util.ArrayList<>();
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

    private void observeSkillMutation(ItemStack itemStack, SkillPdcMutation mutation) {
        if (plugin == null
                || plugin.debugLogger() == null
                || mutation == null
                || !plugin.debugLogger().shouldLog("pdc", (java.util.UUID) null)) {
            return;
        }
        plugin.debugLogger().log("pdc", (java.util.UUID) null, "pdc.skill_payload", Map.of(
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
