package emaki.jiuwu.craft.corelib.integration;

import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;

import org.bukkit.block.Block;

class NoopCustomBlockBridge implements CustomBlockBridge {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public boolean isCustomBlock(Block block) {
        return false;
    }

    @Override
    public String identifyBlock(Block block) {
        return "";
    }

    @Override
    public boolean matches(Block block, String identifier) {
        return false;
    }
}
