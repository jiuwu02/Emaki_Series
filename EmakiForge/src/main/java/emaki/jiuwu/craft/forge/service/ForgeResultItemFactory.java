package emaki.jiuwu.craft.forge.service;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.assembly.EmakiItemAssemblyRequest;
import emaki.jiuwu.craft.corelib.assembly.EmakiItemLayerSnapshot;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeResultItemFactory {

    private final EmakiForgePlugin plugin;
    private final ForgeLayerSnapshotBuilder snapshotBuilder;

    ForgeResultItemFactory(EmakiForgePlugin plugin) {
        this.plugin = plugin;
        this.snapshotBuilder = new ForgeLayerSnapshotBuilder(plugin);
    }

    EmakiItemAssemblyRequest buildAssemblyRequest(Recipe recipe,
            GuiItems guiItems,
            double multiplier,
            QualitySettings.QualityTier qualityTier,
            long forgedAt) {
        if (recipe == null) {
            return null;
        }
        EmakiItemLayerSnapshot forgeLayer = snapshotBuilder.buildLayerSnapshot(recipe, guiItems, multiplier, qualityTier, forgedAt);
        ItemSource baseSource = resolveConfiguredOutputSource(recipe);
        ItemStack existingItem = baseSource == null ? cloneNonAir(guiItems == null ? null : guiItems.targetItem()) : null;
        if (baseSource == null && existingItem == null) {
            return null;
        }
        return new EmakiItemAssemblyRequest(
                baseSource,
                1,
                existingItem,
                java.util.List.of(forgeLayer)
        );
    }

    ItemSource resolveConfiguredOutputSource(Recipe recipe) {
        return recipe == null ? null : recipe.configuredOutputSource();
    }

    ItemStack createConfiguredOutputItem(Recipe recipe) {
        ItemSource source = resolveConfiguredOutputSource(recipe);
        return source == null ? null : plugin.itemIdentifierService().createItem(source, 1);
    }

    String resolveResultItemName(Recipe recipe, ItemStack itemStack) {
        String resolvedItemName = resolveItemName(itemStack);
        if (Texts.isNotBlank(resolvedItemName)) {
            return resolvedItemName;
        }
        ItemSource source = resolveConfiguredOutputSource(recipe);
        if (source != null) {
            String displayName = plugin.itemIdentifierService().displayName(source);
            if (Texts.isNotBlank(displayName)) {
                return displayName;
            }
        }
        return "物品";
    }

    String resolveSourceItemName(GuiItems guiItems, ItemStack resultItem, Recipe recipe) {
        String sourceName = resolveItemName(createConfiguredOutputItem(recipe));
        if (Texts.isBlank(sourceName)) {
            sourceName = resolveItemName(guiItems == null ? null : guiItems.targetItem());
        }
        return Texts.isNotBlank(sourceName) ? sourceName : resolveResultItemName(recipe, resultItem);
    }

    String buildShowItemPlaceholder(GuiItems guiItems, Recipe recipe, ItemStack resultItem) {
        if (resultItem == null || resultItem.getType() == Material.AIR) {
            return resolveSourceItemName(guiItems, resultItem, recipe);
        }
        String displayText = resolveDisplayText(createConfiguredOutputItem(recipe));
        if (Texts.isBlank(displayText)) {
            displayText = resolveDisplayText(guiItems == null ? null : guiItems.targetItem());
        }
        if (Texts.isBlank(displayText)) {
            displayText = resolveDisplayText(resultItem);
        }
        if (Texts.isBlank(displayText)) {
            displayText = resolveResultItemName(recipe, resultItem);
        }
        try {
            return ItemTextBridge.displayWithItemHoverText(displayText, resultItem);
        } catch (Exception ignored) {
            return resolveSourceItemName(guiItems, resultItem, recipe);
        }
    }

    private String resolveItemName(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return "";
        }
        return ItemTextBridge.effectiveNamePlain(itemStack);
    }

    private String resolveDisplayText(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return "";
        }
        return ItemTextBridge.effectiveNameText(itemStack);
    }

    private ItemStack cloneNonAir(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        return itemStack.clone();
    }
}
