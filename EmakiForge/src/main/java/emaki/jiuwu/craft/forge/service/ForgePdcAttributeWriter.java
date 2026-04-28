package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.integration.PdcAttributeGateway;
import emaki.jiuwu.craft.corelib.integration.SkillPdcGateway;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgePdcAttributeWriter {

    private static final String SOURCE_ID = "forge";

    private final EmakiForgePlugin plugin;
    private final SkillPdcGateway skillPdcGateway = new SkillPdcGateway();

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
        PdcAttributeGateway gateway = plugin.pdcAttributeGateway();
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
                meta.putAll(contribution.material().attributeMeta());
            }
        }
        skillPdcGateway.write(itemStack, skillIds);
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
}
