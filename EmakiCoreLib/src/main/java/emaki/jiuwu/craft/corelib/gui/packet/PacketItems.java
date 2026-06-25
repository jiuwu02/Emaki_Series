package emaki.jiuwu.craft.corelib.gui.packet;

import io.github.retrooper.packetevents.util.SpigotConversionUtil;

/**
 * Bukkit ↔ PacketEvents {@code ItemStack} conversion helpers for the packet GUI
 * backend. Null/AIR Bukkit items map to the packet {@code EMPTY} stack.
 */
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
