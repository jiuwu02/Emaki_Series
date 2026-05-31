package emaki.jiuwu.craft.item.service;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.RepairMaterial;

public final class ItemRepairService {

    private static final NamespacedKey DISABLED_KEY = new NamespacedKey("emakiitem", "disabled");

    private final EmakiItemPlugin plugin;

    public ItemRepairService(EmakiItemPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isDisabled(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte value = pdc.get(DISABLED_KEY, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public void markDisabled(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(DISABLED_KEY, PersistentDataType.BYTE, (byte) 1);
        itemStack.setItemMeta(meta);
    }

    public void clearDisabled(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().remove(DISABLED_KEY);
        itemStack.setItemMeta(meta);
    }

    @Nullable
    public RepairMaterial findMatchingMaterial(EmakiItemDefinition definition, ItemStack repairItem) {
        if (definition == null || repairItem == null || repairItem.getType().isAir()) {
            return null;
        }
        if (!definition.repair().enabled() || !definition.repair().hasRepairMaterials()) {
            return null;
        }
        ItemSourceService sourceService = plugin.itemSourceService();
        ItemSource repairItemSource = sourceService.identifyItem(repairItem);
        if (repairItemSource == null) {
            return null;
        }
        for (RepairMaterial material : definition.repair().materials()) {
            ItemSource expectedSource = ItemSourceUtil.parseShorthand(material.itemSource());
            if (expectedSource != null && ItemSourceUtil.matches(repairItemSource, expectedSource)) {
                return material;
            }
        }
        return null;
    }

    public int repair(Player player, ItemStack equipment, ItemStack repairItem, RepairMaterial matched) {
        if (player == null || equipment == null || matched == null) {
            return 0;
        }
        ItemMeta meta = equipment.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return 0;
        }
        int maxDamage = damageable.hasMaxDamage() ? damageable.getMaxDamage() : equipment.getType().getMaxDurability();
        if (maxDamage <= 0) {
            return 0;
        }
        int restoreAmount = matched.resolveAmount(maxDamage);
        if (restoreAmount <= 0) {
            return 0;
        }
        int currentDamage = damageable.getDamage();
        int newDamage = Math.max(0, currentDamage - restoreAmount);
        damageable.setDamage(newDamage);
        equipment.setItemMeta(meta);

        if (newDamage < maxDamage) {
            clearDisabled(equipment);
        }

        return matched.amount();
    }
}
