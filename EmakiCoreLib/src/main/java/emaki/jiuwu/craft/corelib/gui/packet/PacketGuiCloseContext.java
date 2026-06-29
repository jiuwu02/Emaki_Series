package emaki.jiuwu.craft.corelib.gui.packet;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;

/**
 * {@link GuiCloseContext} backed by a virtual packet window. The top inventory
 * contents are read from the window's tracked item array rather than a Bukkit
 * inventory.
 */
final class PacketGuiCloseContext implements GuiCloseContext {

    private final Player player;
    private final PacketGuiBackend.PacketWindow window;

    PacketGuiCloseContext(Player player, PacketGuiBackend.PacketWindow window) {
        this.player = player;
        this.window = window;
    }

    @Override
    public Player player() {
        return player;
    }

    @Override
    public int topInventorySize() {
        return window.topSize();
    }

    @Override
    public ItemStack topInventoryItem(int slot) {
        return window.topItem(slot);
    }
}
