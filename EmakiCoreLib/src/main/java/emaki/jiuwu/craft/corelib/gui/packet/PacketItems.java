package emaki.jiuwu.craft.corelib.gui.packet;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;

final class PacketItems {

    private PacketItems() {
    }

    static ItemStack toPacket(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return ItemStack.EMPTY;
        }
        return SpigotConversionUtil.fromBukkitItemStack(item);
    }
}
