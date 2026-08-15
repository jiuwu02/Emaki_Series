package emaki.jiuwu.craft.storage.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class StorageKey {

    private final ItemStack template;
    private final int hash;

    private StorageKey(ItemStack normalized) {
        this.template = normalized;
        this.hash = normalized.hashCode();
    }

    public static StorageKey of(ItemStack source) {
        ItemStack normalized = source.clone();
        normalized.setAmount(1);
        return new StorageKey(normalized);
    }

    public ItemStack toItemStack() {
        return template.clone();
    }

    public ItemStack toItemStack(int amount) {
        ItemStack copy = template.clone();
        copy.setAmount(Math.max(1, amount));
        return copy;
    }

    public Material material() {
        return template.getType();
    }

    public int vanillaMaxStackSize() {
        return Math.max(1, template.getMaxStackSize());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof StorageKey key && hash == key.hash && template.equals(key.template);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "StorageKey[" + template.getType().getKey() + "]";
    }
}
