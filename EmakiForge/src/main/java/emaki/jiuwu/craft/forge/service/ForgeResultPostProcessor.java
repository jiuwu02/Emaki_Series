package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeResultPostProcessor {

    private static final String OPERATION_NAMESPACE = "forge";

    private final EmakiForgePlugin plugin;
    private final ForgeLayerSnapshotBuilder layerSnapshotBuilder;
    private final ForgePdcAttributeWriter pdcAttributeWriter;
    private final ItemOperationLedger operationLedger;

    ForgeResultPostProcessor(EmakiForgePlugin plugin,
            ForgeLayerSnapshotBuilder layerSnapshotBuilder,
            ForgePdcAttributeWriter pdcAttributeWriter,
            ItemOperationLedger operationLedger) {
        this.plugin = plugin;
        this.layerSnapshotBuilder = layerSnapshotBuilder;
        this.pdcAttributeWriter = pdcAttributeWriter;
        this.operationLedger = operationLedger;
    }

    void process(Recipe recipe,
            GuiItems guiItems,
            ForgeService.PreparedForge preparedForge,
            ItemStack resultItem) {
        if (preparedForge == null || resultItem == null) {
            return;
        }
        pdcAttributeWriter.apply(
                recipe,
                layerSnapshotBuilder.collectMaterialContributions(recipe, guiItems),
                preparedForge.multiplier(),
                preparedForge.qualityTier(),
                resultItem
        );
        applyForgeOperations(recipe, guiItems, preparedForge, resultItem);
    }

    private void applyForgeOperations(Recipe recipe,
            GuiItems guiItems,
            ForgeService.PreparedForge preparedForge,
            ItemStack resultItem) {
        if (recipe == null || resultItem == null) {
            return;
        }
        List<Object> allNameActions = new ArrayList<>();
        List<Object> allLoreActions = new ArrayList<>();

        if (recipe.result() != null) {
            if (recipe.result().nameModifications() != null && !recipe.result().nameModifications().isEmpty()) {
                allNameActions.add(recipe.result().nameModifications());
            }
            if (recipe.result().loreActions() != null && !recipe.result().loreActions().isEmpty()) {
                allLoreActions.add(recipe.result().loreActions());
            }
        }

        List<ForgeMaterialContribution> materials = layerSnapshotBuilder.collectMaterialContributions(recipe, guiItems);
        if (materials != null) {
            for (ForgeMaterialContribution material : materials) {
                if (material == null || material.material() == null) {
                    continue;
                }
                Object matNameActions = material.material().nameModifications();
                Object matLoreActions = material.material().loreActions();
                if (matNameActions != null) {
                    allNameActions.add(matNameActions);
                }
                if (matLoreActions != null) {
                    allLoreActions.add(matLoreActions);
                }
            }
        }

        QualitySettings settings = plugin.appConfig() == null || plugin.appConfig().qualitySettings() == null
                ? QualitySettings.defaults()
                : plugin.appConfig().qualitySettings();
        if (preparedForge.qualityTier() != null && settings.itemMetaEnabled()) {
            Object qualityNameActions = settings.itemMetaNameActions(preparedForge.qualityTier().name());
            Object qualityLoreActions = settings.itemMetaLoreActions(preparedForge.qualityTier().name());
            if (qualityNameActions != null) {
                allNameActions.add(qualityNameActions);
            }
            if (qualityLoreActions != null) {
                allLoreActions.add(qualityLoreActions);
            }
        }

        if (allNameActions.isEmpty() && allLoreActions.isEmpty()) {
            return;
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        if (preparedForge.qualityTier() != null) {
            variables.put("quality", preparedForge.qualityTier().name());
            variables.put("quality_name", preparedForge.qualityTier().name());
        }
        variables.put("quality_multiplier", Numbers.formatNumber(preparedForge.multiplier(), "0.##"));
        variables.put("multiplier", Numbers.formatNumber(preparedForge.multiplier(), "0.##"));

        String operationId = OPERATION_NAMESPACE + ":" + recipe.id();
        Object nameActionsToApply = allNameActions.size() == 1 ? allNameActions.get(0) : allNameActions;
        Object loreActionsToApply = allLoreActions.size() == 1 ? allLoreActions.get(0) : allLoreActions;
        operationLedger.apply(resultItem, operationId, OPERATION_NAMESPACE,
                allNameActions.isEmpty() ? null : nameActionsToApply,
                allLoreActions.isEmpty() ? null : loreActionsToApply,
                variables);
    }
}
