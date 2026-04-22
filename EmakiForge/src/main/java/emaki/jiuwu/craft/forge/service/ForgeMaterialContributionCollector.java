package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeMaterialContributionCollector {

    private final ForgeMaterialUsagePlanner usagePlanner;

    ForgeMaterialContributionCollector(EmakiForgePlugin plugin) {
        this.usagePlanner = new ForgeMaterialUsagePlanner(plugin);
    }

    List<ForgeMaterialContribution> collectMaterialContributions(Recipe recipe, GuiItems guiItems) {
        List<ForgeMaterialContribution> materials = usagePlanner.collectMaterialContributions(recipe, guiItems);
        materials.sort((left, right) -> Integer.compare(left.sequence(), right.sequence()));
        return materials;
    }

    List<ForgeMaterial.QualityModifier> collectQualityModifiers(List<ForgeMaterialContribution> materials) {
        List<ForgeMaterial.QualityModifier> result = new ArrayList<>();
        if (materials == null) {
            return result;
        }
        for (ForgeMaterialContribution material : materials) {
            if (material == null || material.amount() <= 0) {
                continue;
            }
            result.addAll(material.qualityModifiers());
        }
        return result;
    }
}
