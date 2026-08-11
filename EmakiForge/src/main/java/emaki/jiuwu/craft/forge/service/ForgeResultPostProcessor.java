package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
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

    void process(Player player,
            Recipe recipe,
            GuiItems guiItems,
            ForgeService.PreparedForge preparedForge,
            ItemStack resultItem) {
        if (preparedForge == null || resultItem == null) {
            return;
        }
        List<ForgeMaterialContribution> materials = layerSnapshotBuilder.collectMaterialContributions(recipe, guiItems);
        pdcAttributeWriter.apply(
                recipe,
                materials,
                preparedForge.multiplier(),
                preparedForge.qualityTier(),
                resultItem
        );
        applyForgeOperations(player, recipe, preparedForge, resultItem, materials);
    }

    private void applyForgeOperations(Player player,
            Recipe recipe,
            ForgeService.PreparedForge preparedForge,
            ItemStack resultItem,
            List<ForgeMaterialContribution> materials) {
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

        Map<String, Object> variables = buildOperationVariables(materials, preparedForge);

        String operationId = OPERATION_NAMESPACE + ":" + recipe.id();
        Object nameActionsToApply = allNameActions.size() == 1 ? allNameActions.get(0) : allNameActions;
        Object loreActionsToApply = allLoreActions.size() == 1 ? allLoreActions.get(0) : allLoreActions;
        ActionContext context = ActionContext.create(player, "forge.result_meta", false)
                .withPlaceholders(variables)
                .withAttribute("recipe", recipe)
                .withAttribute("resultItem", resultItem)
                .withAttribute("quality", preparedForge == null || preparedForge.qualityTier() == null ? "" : preparedForge.qualityTier().name())
                .withAttribute("multiplier", preparedForge == null ? 1D : preparedForge.multiplier());
        operationLedger.apply(context, resultItem, operationId, OPERATION_NAMESPACE,
                allNameActions.isEmpty() ? null : nameActionsToApply,
                allLoreActions.isEmpty() ? null : loreActionsToApply,
                variables);
    }

    private Map<String, Object> buildOperationVariables(List<ForgeMaterialContribution> materials,
            ForgeService.PreparedForge preparedForge) {
        double multiplier = preparedForge == null ? 1D : preparedForge.multiplier();
        Map<String, Object> variables = new LinkedHashMap<>(layerSnapshotBuilder.buildDisplayVariables(
                materials,
                multiplier,
                plugin.appConfig().defaultNumberFormat()
        ));
        if (preparedForge != null && preparedForge.qualityTier() != null) {
            variables.put("quality", preparedForge.qualityTier().name());
            variables.put("quality_name", preparedForge.qualityTier().name());
        }
        variables.put("quality_multiplier", Numbers.formatNumber(multiplier, "0.##"));
        variables.put("multiplier", Numbers.formatNumber(multiplier, "0.##"));
        return variables;
    }
}
