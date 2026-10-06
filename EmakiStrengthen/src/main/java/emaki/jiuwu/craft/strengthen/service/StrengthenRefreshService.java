package emaki.jiuwu.craft.strengthen.service;

import java.util.Set;
import java.util.TreeSet;

import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.cache.CacheManager;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.item.PlayerItemRefreshService;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

public final class StrengthenRefreshService implements PlayerItemRefreshService {

    private static final int FIRST_ARMOR_SLOT = 36;
    private static final int OFF_HAND_SLOT = 40;
    private static final int REFRESH_GATE_SIZE = 1024;
    private static final long REFRESH_GATE_TTL_MILLIS = 300_000L;
    private static final String ASSEMBLY_SIGNATURE_FIELD = "assembly_signature";
    private static final String LEGACY_ITEM_PARTITION = "item";

    private final EmakiStrengthenPlugin plugin;
    private final StrengthenAttemptService attemptService;
    private final ExecutionDispatcher executionDispatcher;
    private final CacheManager<String, Boolean> refreshedSignatures =
            new CacheManager<>(REFRESH_GATE_SIZE, REFRESH_GATE_TTL_MILLIS);
    private final PdcService pdcService;
    private final PdcPartition itemPartition;

    public StrengthenRefreshService(EmakiStrengthenPlugin plugin,
            StrengthenAttemptService attemptService,
            ExecutionDispatcher executionDispatcher) {
        this.plugin = plugin;
        this.attemptService = attemptService;
        this.executionDispatcher = executionDispatcher;
        this.pdcService = new PdcService("emaki", "strengthen", plugin.debugLogger());
        this.itemPartition = pdcService.partition("item");
    }

    public void refreshOnlinePlayers() {
        refreshedSignatures.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (executionDispatcher.runEntity(
                        plugin, player, () -> refreshPlayerInventory(player)) == null) {
                    plugin.getLogger().warning("玩家刷新的调度被拒绝: " + player.getUniqueId());
                }
            } catch (Throwable throwable) {
                plugin.getLogger().warning("调度玩家刷新失败: " + player.getUniqueId()
                        + ": " + throwable.getMessage());
            }
        }
    }

    @Override
    public void refreshPlayerInventory(Player player) {
        refreshPlayerItems(player);
    }

    @Override
    public void refreshPlayerSlots(Player player, Set<Integer> slots, boolean full) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (full) {
            refreshPlayerInventory(player);
            return;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = null;
        boolean storageChanged = false;
        ItemStack[] armor = null;
        boolean armorChanged = false;
        for (int slot : orderedSlots(slots)) {
            if (slot < FIRST_ARMOR_SLOT) {
                if (storage == null) {
                    storage = inventory.getStorageContents();
                }
                if (slot >= storage.length) {
                    continue;
                }
                ItemStack original = storage[slot];
                ItemStack refreshed = refreshGatedItem(original);
                if (refreshed != original) {
                    storage[slot] = refreshed;
                    storageChanged = true;
                }
            } else if (slot < OFF_HAND_SLOT) {
                if (armor == null) {
                    armor = inventory.getArmorContents();
                }
                int index = slot - FIRST_ARMOR_SLOT;
                if (index >= armor.length) {
                    continue;
                }
                ItemStack original = armor[index];
                ItemStack refreshed = refreshGatedItem(original);
                if (refreshed != original) {
                    armor[index] = refreshed;
                    armorChanged = true;
                }
            } else if (slot == OFF_HAND_SLOT) {
                ItemStack offHand = inventory.getItemInOffHand();
                ItemStack refreshed = refreshGatedItem(offHand);
                if (refreshed != offHand) {
                    inventory.setItemInOffHand(refreshed);
                }
            }
        }
        if (storageChanged) {
            inventory.setStorageContents(storage);
        }
        if (armorChanged) {
            inventory.setArmorContents(armor);
        }
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

    private ItemStack refreshGatedItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return itemStack;
        }
        String signature = assemblySignature(itemStack);
        if (signature == null) {
            return refreshItem(itemStack);
        }
        if (refreshedSignatures.containsKey(signature)) {
            return itemStack;
        }
        ItemStack refreshed = refreshItem(itemStack);
        if (refreshed != itemStack) {
            String rebuiltSignature = assemblySignature(refreshed);
            refreshedSignatures.put(rebuiltSignature == null ? signature : rebuiltSignature, Boolean.TRUE);
        }
        return refreshed;
    }

    private String assemblySignature(ItemStack itemStack) {
        String signature = pdcService.getMigrating(itemStack, itemPartition,
                LEGACY_ITEM_PARTITION, ASSEMBLY_SIGNATURE_FIELD, PersistentDataType.STRING);
        return signature == null || signature.isBlank() ? null : signature;
    }

    private Set<Integer> orderedSlots(Set<Integer> slots) {
        if (slots == null || slots.isEmpty()) {
            return Set.of();
        }
        return new TreeSet<>(slots);
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
