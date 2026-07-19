package emaki.jiuwu.craft.item.action;

import java.util.Locale;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

final class ItemInventorySlot {

    private final String id;
    private final Getter getter;
    private final Setter setter;

    private ItemInventorySlot(String id, Getter getter, Setter setter) {
        this.id = id;
        this.getter = getter;
        this.setter = setter;
    }

    String id() {
        return id;
    }

    ItemStack get(PlayerInventory inventory) {
        ItemStack itemStack = inventory == null ? null : getter.get(inventory);
        return itemStack == null ? null : itemStack.clone();
    }

    void set(PlayerInventory inventory, ItemStack itemStack) {
        if (inventory != null) {
            setter.set(inventory, itemStack == null || itemStack.getType().isAir() ? null : itemStack.clone());
        }
    }

    static ItemInventorySlot parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "main", "mainhand", "main_hand", "hand", "held", "held_item", "selected" -> named(
                    "mainhand", PlayerInventory::getItemInMainHand, PlayerInventory::setItemInMainHand);
            case "offhand", "off_hand" -> named(
                    "offhand", PlayerInventory::getItemInOffHand, PlayerInventory::setItemInOffHand);
            case "head", "helmet", "armor_head" -> named(
                    "helmet", PlayerInventory::getHelmet, PlayerInventory::setHelmet);
            case "chest", "chestplate", "armor_chest" -> named(
                    "chestplate", PlayerInventory::getChestplate, PlayerInventory::setChestplate);
            case "legs", "leggings", "armor_legs" -> named(
                    "leggings", PlayerInventory::getLeggings, PlayerInventory::setLeggings);
            case "feet", "boots", "armor_feet" -> named(
                    "boots", PlayerInventory::getBoots, PlayerInventory::setBoots);
            default -> indexed(normalized);
        };
    }

    private static ItemInventorySlot named(String id, Getter getter, Setter setter) {
        return new ItemInventorySlot(id, getter, setter);
    }

    private static ItemInventorySlot indexed(String raw) {
        boolean hotbar = raw.startsWith("hotbar_");
        String numeric = raw;
        if (raw.startsWith("slot_")) {
            numeric = raw.substring("slot_".length());
        } else if (raw.startsWith("inventory_")) {
            numeric = raw.substring("inventory_".length());
        } else if (hotbar) {
            numeric = raw.substring("hotbar_".length());
        }
        int index;
        try {
            index = Integer.parseInt(numeric);
        } catch (NumberFormatException ignored) {
            return null;
        }
        int maximum = hotbar ? 8 : 40;
        if (index < 0 || index > maximum) {
            return null;
        }
        return new ItemInventorySlot(
                "slot_" + index,
                inventory -> inventory.getItem(index),
                (inventory, itemStack) -> inventory.setItem(index, itemStack)
        );
    }

    @FunctionalInterface
    private interface Getter {
        ItemStack get(PlayerInventory inventory);
    }

    @FunctionalInterface
    private interface Setter {
        void set(PlayerInventory inventory, ItemStack itemStack);
    }
}
