package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.text.Texts;

final class ActionInventorySlot {

    private final String id;
    private final Getter getter;
    private final Setter setter;

    private ActionInventorySlot(String id, Getter getter, Setter setter) {
        this.id = id;
        this.getter = getter;
        this.setter = setter;
    }

    String id() {
        return id;
    }

    ItemStack get(PlayerInventory inventory) {
        return inventory == null ? null : getter.get(inventory);
    }

    void set(PlayerInventory inventory, ItemStack itemStack) {
        if (inventory != null) {
            setter.set(inventory, itemStack == null || itemStack.getType().isAir() ? null : itemStack);
        }
    }

    void clear(PlayerInventory inventory) {
        set(inventory, new ItemStack(Material.AIR));
    }

    static ActionInventorySlot parse(String raw, String fallback) {
        String value = Texts.isBlank(raw) ? fallback : raw;
        if (Texts.isBlank(value)) {
            return null;
        }
        String normalized = Texts.trim(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mainhand", "main_hand", "hand" -> named("mainhand", PlayerInventory::getItemInMainHand, PlayerInventory::setItemInMainHand);
            case "offhand", "off_hand" -> named("offhand", PlayerInventory::getItemInOffHand, PlayerInventory::setItemInOffHand);
            case "helmet" -> named("helmet", PlayerInventory::getHelmet, PlayerInventory::setHelmet);
            case "chestplate", "chest" -> named("chestplate", PlayerInventory::getChestplate, PlayerInventory::setChestplate);
            case "leggings", "legs" -> named("leggings", PlayerInventory::getLeggings, PlayerInventory::setLeggings);
            case "boots" -> named("boots", PlayerInventory::getBoots, PlayerInventory::setBoots);
            default -> indexed(normalized);
        };
    }

    private static ActionInventorySlot named(String id, Getter getter, Setter setter) {
        return new ActionInventorySlot(id, getter, setter);
    }

    private static ActionInventorySlot indexed(String raw) {
        String numeric = raw.startsWith("slot_") ? raw.substring("slot_".length()) : raw;
        if (raw.startsWith("hotbar_")) {
            numeric = raw.substring("hotbar_".length());
        }
        Integer index = ActionParsers.parseIntNullable(numeric);
        if (index == null || index < 0 || index > 35) {
            return null;
        }
        return new ActionInventorySlot(
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
