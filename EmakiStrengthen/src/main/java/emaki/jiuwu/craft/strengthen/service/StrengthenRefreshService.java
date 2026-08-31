package emaki.jiuwu.craft.strengthen.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.item.PlayerItemRefreshService;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

public final class StrengthenRefreshService implements PlayerItemRefreshService {

    private final EmakiStrengthenPlugin plugin;
    private final StrengthenAttemptService attemptService;
    private final ExecutionDispatcher executionDispatcher;

    public StrengthenRefreshService(EmakiStrengthenPlugin plugin,
            StrengthenAttemptService attemptService,
            ExecutionDispatcher executionDispatcher) {
        this.plugin = plugin;
        this.attemptService = attemptService;
        this.executionDispatcher = executionDispatcher;
    }

    public void refreshOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (executionDispatcher.runEntity(
                        plugin, player, () -> refreshPlayerInventory(player)) == null) {
                    plugin.getLogger().warning("Player refresh scheduling was rejected for " + player.getUniqueId());
                }
            } catch (Throwable throwable) {
                plugin.getLogger().warning("Failed to schedule player refresh for " + player.getUniqueId()
                        + ": " + throwable.getMessage());
            }
        }
    }

    @Override
    public void refreshPlayerInventory(Player player) {
        refreshPlayerItems(player);
    }

    public int refreshPlayerItems(Player player) {
        if (player == null || !player.isOnline()) {
            return 0;
        }
        PlayerInventory inventory = player.getInventory();
        int refreshedCount = 0;
        ItemStack[] storage = inventory.getStorageContents();
        int storageChanged = refreshArray(storage);
        if (storageChanged > 0) {
            inventory.setStorageContents(storage);
            refreshedCount += storageChanged;
        }
        ItemStack[] armor = inventory.getArmorContents();
        int armorChanged = refreshArray(armor);
        if (armorChanged > 0) {
            inventory.setArmorContents(armor);
            refreshedCount += armorChanged;
        }
        ItemStack offHand = inventory.getItemInOffHand();
        ItemStack refreshedOffHand = refreshItem(offHand);
        if (refreshedOffHand != offHand) {
            inventory.setItemInOffHand(refreshedOffHand);
            refreshedCount++;
        }
        return refreshedCount;
    }

    @Override
    public void refreshDroppedItem(Item itemEntity) {
        if (itemEntity == null || !itemEntity.isValid()) {
            return;
        }
        ItemStack refreshed = refreshItem(itemEntity.getItemStack());
        if (refreshed != itemEntity.getItemStack()) {
            itemEntity.setItemStack(refreshed);
        }
    }

    public ItemStack refreshItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return itemStack;
        }
        try {
            ItemStack rebuilt = attemptService.rebuild(itemStack);
            if (rebuilt == null) {
                plugin.getLogger().warning("刷新失败：rebuild 返回 null | material=" + itemStack.getType().name());
                return itemStack;
            }
            return rebuilt;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("刷新失败：rebuild 抛出异常 | material=" + itemStack.getType().name()
                    + " | error=" + exception.getMessage());
            return itemStack;
        }
    }

    private int refreshArray(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return 0;
        }
        int changed = 0;
        for (int index = 0; index < items.length; index++) {
            ItemStack original = items[index];
            ItemStack refreshed = refreshItem(original);
            if (refreshed != original) {
                items[index] = refreshed;
                changed++;
            }
        }
        return changed;
    }
}
