package emaki.jiuwu.craft.corelib.gui.packet;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;

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
