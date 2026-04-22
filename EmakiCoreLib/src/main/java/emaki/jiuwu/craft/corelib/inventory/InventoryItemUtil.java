package emaki.jiuwu.craft.corelib.inventory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;

public final class InventoryItemUtil {

    private InventoryItemUtil() {
    }

    public static long countItems(Player player, ItemSourceService itemSourceService, String itemToken) {
        return countItems(player, itemSourceService, ItemSourceUtil.parse(itemToken));
    }

    public static long countItems(Player player, ItemSourceService itemSourceService, ItemSource targetSource) {
        if (player == null) {
            return 0L;
        }
        return countItems(player.getInventory() == null ? null : player.getInventory().getContents(), itemSourceService, targetSource);
    }

    public static long countItems(ItemStack[] contents, ItemSourceService itemSourceService, String itemToken) {
        return countItems(contents, itemSourceService, ItemSourceUtil.parse(itemToken));
    }

    public static long countItems(ItemStack[] contents, ItemSourceService itemSourceService, ItemSource targetSource) {
        if (contents == null || contents.length == 0 || itemSourceService == null || targetSource == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemStack itemStack : contents) {
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            ItemSource source = itemSourceService.identifyItem(itemStack);
            if (ItemSourceUtil.matches(targetSource, source)) {
                total += itemStack.getAmount();
            }
        }
        return total;
    }

    public static long countItems(Map<Integer, ItemStack> items, ItemSourceService itemSourceService, ItemSource targetSource) {
        if (items == null || items.isEmpty() || itemSourceService == null || targetSource == null) {
            return 0L;
        }
        long total = 0L;
        for (ItemStack itemStack : items.values()) {
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            ItemSource source = itemSourceService.identifyItem(itemStack);
            if (ItemSourceUtil.matches(targetSource, source)) {
                total += itemStack.getAmount();
            }
        }
        return total;
    }

    public static boolean removeItems(PlayerInventory inventory,
            ItemSourceService itemSourceService,
            String itemToken,
            long amount) {
        return removeItems(inventory, itemSourceService, ItemSourceUtil.parse(itemToken), amount);
    }

    public static boolean removeItems(PlayerInventory inventory,
            ItemSourceService itemSourceService,
            ItemSource targetSource,
            long amount) {
        if (inventory == null || itemSourceService == null || targetSource == null || amount <= 0L) {
            return amount <= 0L;
        }
        long remaining = amount;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length && remaining > 0L; slot++) {
            ItemStack itemStack = contents[slot];
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            ItemSource source = itemSourceService.identifyItem(itemStack);
            if (!ItemSourceUtil.matches(targetSource, source)) {
                continue;
            }
            int take = (int) Math.min(remaining, itemStack.getAmount());
            itemStack.setAmount(itemStack.getAmount() - take);
            remaining -= take;
            contents[slot] = itemStack.getAmount() <= 0 ? null : itemStack;
        }
        inventory.setContents(contents);
        return remaining <= 0L;
    }

    public static long removeItems(Map<Integer, ItemStack> items,
            ItemSourceService itemSourceService,
            ItemSource targetSource,
            long amount) {
        if (items == null || items.isEmpty() || itemSourceService == null || targetSource == null || amount <= 0L) {
            return amount;
        }
        long remaining = amount;
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            ItemStack itemStack = entry.getValue();
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            ItemSource source = itemSourceService.identifyItem(itemStack);
            if (!ItemSourceUtil.matches(targetSource, source)) {
                continue;
            }
            int take = (int) Math.min(remaining, itemStack.getAmount());
            itemStack.setAmount(itemStack.getAmount() - take);
            remaining -= take;
            if (itemStack.getAmount() <= 0) {
                entry.setValue(null);
            }
            if (remaining <= 0L) {
                break;
            }
        }
        items.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().getType().isAir());
        return remaining;
    }

    public static Map<Integer, ItemStack> addOrDrop(Player player, ItemStack itemStack) {
        ItemStack clone = cloneNonAir(itemStack);
        if (player == null || clone == null) {
            return Map.of();
        }
        Map<Integer, ItemStack> leftover = new LinkedHashMap<>(player.getInventory().addItem(clone));
        for (ItemStack left : leftover.values()) {
            ItemStack drop = cloneNonAir(left);
            if (drop != null) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
        return leftover.isEmpty() ? Map.of() : Map.copyOf(leftover);
    }

    public static void giveOrDrop(Player player, ItemStack itemStack) {
        addOrDrop(player, itemStack);
    }

    public static ItemStack cloneNonAir(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        return itemStack.clone();
    }

    /**
     * Temporarily replaces the player's main hand and off hand with the given items,
     * executes the supplier, then unconditionally restores the original held items.
     *
     * @param player the player whose hands to temporarily replace
     * @param mainHand the temporary main hand item (may be null)
     * @param offHand the temporary off hand item (may be null)
     * @param supplier the operation to execute while hands are replaced
     * @param <T> the result type
     * @return a result containing the supplier's return value and post-execution hand snapshots
     */
    public static <T> TemporaryHandsResult<T> withTemporaryHands(Player player,
            ItemStack mainHand,
            ItemStack offHand,
            Supplier<T> supplier) {
        ItemStack originalMainHand = cloneNonAir(player.getInventory().getItemInMainHand());
        ItemStack originalOffHand = cloneNonAir(player.getInventory().getItemInOffHand());
        try {
            player.getInventory().setItemInMainHand(cloneNonAir(mainHand));
            player.getInventory().setItemInOffHand(cloneNonAir(offHand));
            T result = supplier.get();
            ItemStack updatedMainHand = cloneNonAir(player.getInventory().getItemInMainHand());
            ItemStack updatedOffHand = cloneNonAir(player.getInventory().getItemInOffHand());
            return new TemporaryHandsResult<>(result, updatedMainHand, updatedOffHand);
        } finally {
            player.getInventory().setItemInMainHand(originalMainHand);
            player.getInventory().setItemInOffHand(originalOffHand);
        }
    }

    public record TemporaryHandsResult<T>(T result, ItemStack updatedMainHand, ItemStack updatedOffHand) {

    }
}
