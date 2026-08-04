package emaki.jiuwu.craft.corelib.integration;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.integration.CustomBlockBridge;

import org.bukkit.block.Block;

import io.th0rgal.oraxen.api.OraxenBlocks;
import io.th0rgal.oraxen.mechanics.Mechanic;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class OraxenBlockBridgeApi implements CustomBlockBridge {

    @Override
    public boolean available() {
        try {
            Class.forName("io.th0rgal.oraxen.api.OraxenBlocks");
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
            return OraxenBlocks.isOraxenBlock(block);
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
            if (!OraxenBlocks.isOraxenBlock(block)) {
                return "";
            }
            Mechanic mechanic = OraxenBlocks.getOraxenBlock(block.getLocation());
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

    @Override
    public boolean setLit(Block block, boolean lit) {
        return false;
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
            if (!OraxenBlocks.isOraxenBlock(normalized)) {
                return false;
            }
            OraxenBlocks.place(normalized, block.getLocation());
            return matches(block, normalized);
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
