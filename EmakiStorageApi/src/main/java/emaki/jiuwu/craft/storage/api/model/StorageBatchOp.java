package emaki.jiuwu.craft.storage.api.model;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * One signed increment inside a {@link StorageBatchRequest}.
 *
 * <p>Item identity is full {@link ItemStack#equals(Object)}: components, enchantments and persistent
 * data all count. Two stacks that merely share a material or an item-source id are different entries.
 *
 * @param template the item identity; the stored copy always has {@code amount == 1}
 * @param delta    signed unit count &mdash; negative withdraws, positive deposits, zero is rejected
 */
public record StorageBatchOp(@NotNull ItemStack template, long delta) {

    /**
     * Clones the template and normalises its stack amount to one, so the request cannot be mutated
     * by the caller after submission and the stack amount can never be mistaken for {@code delta}.
     */
    public StorageBatchOp {
        ItemStack normalized = template.clone();
        normalized.setAmount(1);
        template = normalized;
    }

    /** {@return a fresh copy of the template; safe to mutate} */
    @Override
    public @NotNull ItemStack template() {
        return template.clone();
    }

    /** {@return whether this op takes units out of storage} */
    public boolean withdrawal() {
        return delta < 0L;
    }

    /** {@return the unsigned unit count} */
    public long magnitude() {
        return Math.abs(delta);
    }
}
