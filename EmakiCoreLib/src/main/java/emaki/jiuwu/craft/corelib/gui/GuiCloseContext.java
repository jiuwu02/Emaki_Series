package emaki.jiuwu.craft.corelib.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Backend-neutral view of a GUI close.
 *
 * <p>Most handlers recover a leftover cursor item and return stored inputs on
 * close, using only the player. GemUpgrade additionally reads the top
 * inventory contents at close time to clone the pending target gem, so this
 * context exposes the top inventory size and per-slot read access.</p>
 */
public interface GuiCloseContext {

    Player player();

    /**
     * The size of the top (GUI) inventory at close time.
     */
    int topInventorySize();

    /**
     * Reads the item at a top-inventory slot at close time. May return null.
     */
    ItemStack topInventoryItem(int slot);
}
