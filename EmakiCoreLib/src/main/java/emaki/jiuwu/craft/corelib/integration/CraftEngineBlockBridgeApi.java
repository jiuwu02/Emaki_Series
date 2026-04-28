package emaki.jiuwu.craft.corelib.integration;

import java.util.Locale;

import org.bukkit.block.Block;

import emaki.jiuwu.craft.corelib.text.Texts;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.CustomBlock;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.registry.Holder;
import net.momirealms.craftengine.core.util.Key;

final class CraftEngineBlockBridgeApi implements CraftEngineBlockBridge {

    @Override
    public boolean available() {
        try {
            return CraftEngineBlocks.loadedBlocks() != null;
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    @Override
    public boolean isCustomBlock(Block block) {
        if (block == null) {
            return false;
        }
        try {
            return CraftEngineBlocks.isCustomBlock(block);
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
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            if (state == null) {
                return "";
            }
            Holder<CustomBlock> owner = state.owner();
            CustomBlock customBlock = owner == null ? null : owner.value();
            Key key = customBlock == null ? null : customBlock.id();
            return normalizeCraftEngineId(key == null ? "" : key.asString());
        } catch (RuntimeException | LinkageError exception) {
            return "";
        }
    }

    @Override
    public boolean matches(Block block, String identifier) {
        String actual = normalizeCraftEngineId(identifyBlock(block));
        String expected = normalizeCraftEngineId(identifier);
        return Texts.isNotBlank(actual) && actual.equals(expected);
    }

    private String normalizeCraftEngineId(Object raw) {
        String text = Texts.trim(raw);
        if (Texts.isBlank(text)) {
            return "";
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("craftengine-")) {
            return normalized.substring("craftengine-".length());
        }
        if (normalized.startsWith("craftengine ")) {
            return normalized.substring("craftengine ".length());
        }
        return normalized;
    }
}
