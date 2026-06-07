package emaki.jiuwu.craft.cooking.service;

import emaki.jiuwu.craft.cooking.model.StationBreakContext;
import emaki.jiuwu.craft.cooking.model.StationInteraction;
import emaki.jiuwu.craft.cooking.model.StationType;
import org.bukkit.block.Block;

import emaki.jiuwu.craft.corelib.api.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceType;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import org.bukkit.Material;

public final class CookingBlockMatcher {

    private final CookingSettingsService settingsService;
    private final CraftEngineBlockBridge craftEngineBlockBridge;
    private final CustomBlockBridge itemsAdderBlockBridge;
    private final CustomBlockBridge nexoBlockBridge;

    public CookingBlockMatcher(CookingSettingsService settingsService,
            CraftEngineBlockBridge craftEngineBlockBridge,
            CustomBlockBridge itemsAdderBlockBridge,
            CustomBlockBridge nexoBlockBridge) {
        this.settingsService = settingsService;
        this.craftEngineBlockBridge = craftEngineBlockBridge;
        this.itemsAdderBlockBridge = itemsAdderBlockBridge;
        this.nexoBlockBridge = nexoBlockBridge;
    }

    public boolean matches(StationInteraction interaction, StationType stationType) {
        if (interaction == null) {
            return false;
        }
        return matches(interaction.block(), stationType, interaction.stationSource());
    }

    public boolean matches(StationBreakContext context, StationType stationType) {
        if (context == null) {
            return false;
        }
        return matches(context.block(), stationType, context.stationSource());
    }

    public boolean matches(Block block, StationType stationType) {
        return matches(block, stationType, null);
    }

    public boolean matches(Block block, StationType stationType, ItemSource stationSource) {
        if (stationType == null) {
            return false;
        }
        for (ItemSource source : settingsService.stationBlockSources(stationType)) {
            if (matches(block, source) || sourceMatches(source, stationSource)) {
                return true;
            }
        }
        return false;
    }

    public boolean matches(Block block, ItemSource source) {
        if (block == null || source == null || source.getType() == null) {
            return false;
        }
        return switch (source.getType()) {
            case VANILLA -> matchesVanilla(block, source.getIdentifier());
            case CRAFTENGINE -> craftEngineBlockBridge != null && craftEngineBlockBridge.matches(block, source.getIdentifier());
            case ITEMSADDER -> itemsAdderBlockBridge != null && itemsAdderBlockBridge.matches(block, source.getIdentifier());
            case NEXO -> nexoBlockBridge != null && nexoBlockBridge.matches(block, source.getIdentifier());
            default -> false;
        };
    }

    private boolean sourceMatches(ItemSource expected, ItemSource actual) {
        return ItemSourceUtil.matches(expected, actual);
    }

    public boolean place(Block block, ItemSource source) {
        if (block == null || source == null || source.getType() == null) {
            return false;
        }
        return switch (source.getType()) {
            case VANILLA -> placeVanilla(block, source.getIdentifier());
            case CRAFTENGINE -> craftEngineBlockBridge != null && craftEngineBlockBridge.placeBlock(block, source.getIdentifier());
            case ITEMSADDER -> itemsAdderBlockBridge != null && itemsAdderBlockBridge.placeBlock(block, source.getIdentifier());
            case NEXO -> nexoBlockBridge != null && nexoBlockBridge.placeBlock(block, source.getIdentifier());
            default -> false;
        };
    }

    public boolean setCustomLit(Block block, boolean lit) {
        if (block == null) {
            return false;
        }
        if (craftEngineBlockBridge != null && craftEngineBlockBridge.isCustomBlock(block)) {
            return craftEngineBlockBridge.setLit(block, lit);
        }
        if (itemsAdderBlockBridge != null && itemsAdderBlockBridge.isCustomBlock(block)) {
            return itemsAdderBlockBridge.setLit(block, lit);
        }
        if (nexoBlockBridge != null && nexoBlockBridge.isCustomBlock(block)) {
            return nexoBlockBridge.setLit(block, lit);
        }
        return false;
    }

    private boolean matchesVanilla(Block block, String identifier) {
        Material expected = resolveMaterial(identifier);
        return expected != null && block.getType() == expected;
    }

    private boolean placeVanilla(Block block, String identifier) {
        Material material = resolveMaterial(identifier);
        if (material == null || material == Material.AIR) {
            return false;
        }
        block.setType(material);
        return block.getType() == material;
    }

    private Material resolveMaterial(String identifier) {
        return ItemSourceUtil.resolveVanillaMaterial(identifier);
    }
}
