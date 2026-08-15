package emaki.jiuwu.craft.corelib.gui.packet;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;

final class PacketItems {

    private PacketItems() {
    }

    static com.github.retrooper.packetevents.protocol.item.ItemStack toPacket(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return com.github.retrooper.packetevents.protocol.item.ItemStack.EMPTY;
        }
        return SpigotConversionUtil.fromBukkitItemStack(item);
    }
}
