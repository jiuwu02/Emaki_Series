package emaki.jiuwu.craft.strengthen.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

public final class StrengthenRefreshService {

    private final EmakiStrengthenPlugin plugin;
    private final StrengthenAttemptService attemptService;

    public StrengthenRefreshService(EmakiStrengthenPlugin plugin, StrengthenAttemptService attemptService) {
        this.plugin = plugin;
        this.attemptService = attemptService;
    }

    public void refreshOnlinePlayers() {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, this::refreshOnlinePlayers);
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayerInventory(player);
        }
    }

    public void refreshPlayerInventory(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        boolean storageChanged = refreshArray(storage);
        if (storageChanged) {
            inventory.setStorageContents(storage);
        }
        ItemStack[] armor = inventory.getArmorContents();
        boolean armorChanged = refreshArray(armor);
        if (armorChanged) {
            inventory.setArmorContents(armor);
        }
        ItemStack offHand = inventory.getItemInOffHand();
        ItemStack refreshedOffHand = refreshItem(offHand);
        if (refreshedOffHand != offHand) {
            inventory.setItemInOffHand(refreshedOffHand);
        }
    }

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
        ItemStack rebuilt = attemptService.rebuild(itemStack);
        return rebuilt == null ? itemStack : rebuilt;
    }

    private boolean refreshArray(ItemStack[] items) {
        if (items == null || items.length == 0) {
            return false;
        }
        boolean changed = false;
        for (int index = 0; index < items.length; index++) {
            ItemStack original = items[index];
            ItemStack refreshed = refreshItem(original);
            if (refreshed != original) {
                items[index] = refreshed;
                changed = true;
            }
        }
        return changed;
    }
}
