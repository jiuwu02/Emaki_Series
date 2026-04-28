package emaki.jiuwu.craft.corelib.integration;

import java.util.Locale;

import org.bukkit.block.Block;

import com.nexomc.nexo.api.NexoBlocks;
import com.nexomc.nexo.mechanics.custom_block.CustomBlockMechanic;
import emaki.jiuwu.craft.corelib.text.Texts;

final class NexoBlockBridgeApi implements CustomBlockBridge {

    @Override
    public boolean available() {
        try {
            Class.forName("com.nexomc.nexo.api.NexoBlocks");
            return true;
        } catch (RuntimeException | LinkageError | ClassNotFoundException exception) {
            return false;
        }
    }

    @Override
    public boolean isCustomBlock(Block block) {
        if (block == null) {
            return false;
        }
        try {
            return NexoBlocks.isCustomBlock(block);
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @Override
    public String identifyBlock(Block block) {
        if (block == null) {
            return "";
        }
        try {
            CustomBlockMechanic mechanic = NexoBlocks.customBlockMechanic(block);
            if (mechanic == null) {
                return "";
            }
            String itemId = mechanic.getItemID();
            return Texts.isBlank(itemId) ? "" : itemId.toLowerCase(Locale.ROOT);
        } catch (RuntimeException | LinkageError exception) {
            return "";
        }
    }

    @Override
    public boolean matches(Block block, String identifier) {
        String actual = identifyBlock(block);
        String expected = normalizeId(identifier);
        return Texts.isNotBlank(actual) && actual.equals(expected);
    }

    private String normalizeId(String raw) {
        String text = Texts.trim(raw);
        if (Texts.isBlank(text)) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT);
    }
}
