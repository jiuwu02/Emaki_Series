package emaki.jiuwu.craft.cooking.service;

import emaki.jiuwu.craft.cooking.model.StationType;
import org.bukkit.block.Block;

import emaki.jiuwu.craft.corelib.integration.CraftEngineBlockBridge;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceType;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import org.bukkit.Material;

public final class CookingBlockMatcher {

    private final CookingSettingsService settingsService;
    private final CraftEngineBlockBridge craftEngineBlockBridge;

    public CookingBlockMatcher(CookingSettingsService settingsService, CraftEngineBlockBridge craftEngineBlockBridge) {
        this.settingsService = settingsService;
        this.craftEngineBlockBridge = craftEngineBlockBridge;
    }

    public boolean matches(Block block, StationType stationType) {
        if (block == null || stationType == null) {
            return false;
        }
        ItemSource source = settingsService.stationBlockSource(stationType);
        return matches(block, source);
    }

    public boolean matches(Block block, ItemSource source) {
        if (block == null || source == null || source.getType() == null) {
            return false;
        }
        return switch (source.getType()) {
            case VANILLA -> matchesVanilla(block, source.getIdentifier());
            case CRAFTENGINE -> craftEngineBlockBridge != null && craftEngineBlockBridge.matches(block, source.getIdentifier());
            default -> false;
        };
    }

    private boolean matchesVanilla(Block block, String identifier) {
        Material expected = resolveMaterial(identifier);
        return expected != null && block.getType() == expected;
    }

    private Material resolveMaterial(String identifier) {
        return ItemSourceUtil.resolveVanillaMaterial(identifier);
    }
}
