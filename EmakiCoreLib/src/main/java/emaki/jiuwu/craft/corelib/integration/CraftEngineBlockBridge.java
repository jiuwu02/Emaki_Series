package emaki.jiuwu.craft.corelib.integration;

import org.bukkit.block.Block;

public interface CraftEngineBlockBridge {

    boolean available();

    boolean isCustomBlock(Block block);

    String identifyBlock(Block block);

    boolean matches(Block block, String identifier);
}
