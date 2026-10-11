package emaki.jiuwu.craft.structure.generate;

import emaki.jiuwu.craft.structure.template.nbt.NbtValue;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockEntityWriter {

    private static final String FIELD_ITEMS = "Items";
    private static final String FIELD_ID = "id";
    private static final String FIELD_SLOT = "Slot";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_LEGACY_COUNT = "Count";

    private final Set<String> unsupportedKinds = ConcurrentHashMap.newKeySet();

    public int unsupportedKindCount() {
        return unsupportedKinds.size();
    }

    public void apply(World world, int x, int y, int z, NbtValue.CompoundTag nbt) {
        if (nbt == null) {
            return;
        }
        Block block = world.getBlockAt(x, y, z);
        if (!(block.getState() instanceof Container container)) {
            unsupportedKinds.add(nbt.getString(FIELD_ID).orElse("unknown"));
            return;
        }
        NbtValue.ListTag items = nbt.getList(FIELD_ITEMS).orElse(null);
        if (items == null) {
            return;
        }
        Inventory inventory = container.getInventory();
        inventory.clear();
        for (NbtValue value : items.values()) {
            if (!(value instanceof NbtValue.CompoundTag item)) {
                continue;
            }
            ItemStack stack = toItemStack(item);
            if (stack == null) {
                continue;
            }
            int slot = item.getByte(FIELD_SLOT).orElse((byte) -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            inventory.setItem(slot, stack);
        }
    }

    private ItemStack toItemStack(NbtValue.CompoundTag item) {
        String rawId = item.getString(FIELD_ID).orElse(null);
        if (rawId == null) {
            return null;
        }
        Material material = Material.matchMaterial(rawId);
        if (material == null || !material.isItem()) {
            return null;
        }
        int count = item.getByte(FIELD_COUNT)
                .or(() -> item.getByte(FIELD_LEGACY_COUNT))
                .orElse((byte) 1);
        int amount = Math.max(1, Math.min(99, count));
        return new ItemStack(material, amount);
    }
}
