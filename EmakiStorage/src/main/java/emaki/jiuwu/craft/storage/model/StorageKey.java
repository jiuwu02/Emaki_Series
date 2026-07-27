package emaki.jiuwu.craft.storage.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Immutable normalised item key.
 *
 * <p><strong>Invariant that must never be broken:</strong> the wrapped template instance is
 * created inside {@link #of(ItemStack)} and is never mutated nor handed out afterwards. A
 * {@code HashMap} key mutated from outside becomes permanently unreachable, which for this module
 * means silently orphaned player items. Every accessor returns a {@code clone()}.
 *
 * <p>Deduplication uses full {@link ItemStack#equals(Object)} — components, enchantments, custom
 * PDC and all. No hash signature layer is introduced: a signature collision would merge two
 * different items into one entry, which is worse than being slow.
 */
public final class StorageKey {

    private final ItemStack template;
    private final int hash;

    private StorageKey(ItemStack normalized) {
        this.template = normalized;
        this.hash = normalized.hashCode();
    }

    /**
     * Builds a key from any item stack.
     *
     * @param source the source item; neither stored nor mutated
     * @return a normalised key whose template always has {@code amount == 1}
     */
    public static StorageKey of(ItemStack source) {
        ItemStack normalized = source.clone();
        normalized.setAmount(1);
        return new StorageKey(normalized);
    }

    /**
     * {@return a fresh copy of the stored item}
     *
     * <p><strong>Withdrawal must use this method.</strong> Never hand the player the GUI's
     * rendered item: that projection carries percentage lore and an overridden
     * {@code max_stack_size}, and using it would bake display text into the player's item
     * permanently.
     */
    public ItemStack toItemStack() {
        return template.clone();
    }

    /**
     * {@return a copy with the requested amount, clamped to at least one}
     *
     * <p>Same rule as {@link #toItemStack()}: this is the data-layer item, not a rendered one.
     */
    public ItemStack toItemStack(int amount) {
        ItemStack copy = template.clone();
        copy.setAmount(Math.max(1, amount));
        return copy;
    }

    /** {@return the item type, used for language-independent sorting and log identifiers} */
    public Material material() {
        return template.getType();
    }

    /** {@return the vanilla stack size of the stored item, used to split withdrawal stacks} */
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
