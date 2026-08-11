package emaki.jiuwu.craft.corelib.integration;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;

import org.bukkit.block.Block;

import dev.lone.itemsadder.api.CustomBlock;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class ItemsAdderBlockBridgeApi implements CustomBlockBridge {

    @Override
    public boolean available() {
        try {
            Class.forName("dev.lone.itemsadder.api.CustomBlock");
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
            return CustomBlock.byAlreadyPlaced(block) != null;
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
            CustomBlock customBlock = CustomBlock.byAlreadyPlaced(block);
            if (customBlock == null) {
                return "";
            }
            String namespacedId = customBlock.getNamespacedID();
            return Texts.isBlank(namespacedId) ? "" : namespacedId.toLowerCase(Locale.ROOT);
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

    @Override
    public boolean setLit(Block block, boolean lit) {
        if (block == null) {
            return false;
        }
        try {
            CustomBlock customBlock = CustomBlock.byAlreadyPlaced(block);
            if (customBlock == null) {
                return false;
            }
            int lightLevel = lit ? Math.max(1, customBlock.getOriginalLightLevel()) : 0;
            customBlock.setCurrentLightLevel(lightLevel);
            return true;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @Override
    public boolean placeBlock(Block block, String identifier) {
        if (block == null) {
            return false;
        }
        String normalized = normalizeId(identifier);
        if (Texts.isBlank(normalized)) {
            return false;
        }
        try {
            if (!CustomBlock.isInRegistry(normalized)) {
                return false;
            }
            return CustomBlock.place(normalized, block.getLocation()) != null;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private String normalizeId(String raw) {
        String text = Texts.trim(raw);
        if (Texts.isBlank(text)) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT);
    }
}
