package emaki.jiuwu.craft.corelib.api.action;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Mutable item holder a caller passes through a pipeline to get a replacement stack back.
 *
 * <p>A pipeline reports success or failure, not values, so a caller that needs the item the pipeline
 * produced cannot read it from the outcome. It keeps one of these instead: hand it to the stages, wait
 * for the pipeline to finish, then read the latest stack and perform the inventory, GUI or transaction
 * commit itself. Keeping the commit outside the pipeline is deliberate, because a partially applied
 * item change is far worse than a rejected one.</p>
 *
 * <p>The holder clones every value crossing its boundary, so neither side can mutate the other's stack
 * by accident. Its volatile reference makes completed stage writes visible to the thread that later
 * reads the result, which matters because a stage may have run on another thread or region.</p>
 */
public final class CoreActionItemTarget {

    public static final String ATTRIBUTE_KEY = "item_target";

    private volatile ItemStack itemStack;

    public CoreActionItemTarget(@NotNull ItemStack itemStack) {
        this.itemStack = cloneItem(itemStack);
    }

    /** {@return a clone of the latest item stack stored by the action chain} */
    public @NotNull ItemStack itemStack() {
        return itemStack.clone();
    }

    /** Replaces the stored item with a clone of the supplied stack. */
    public void setItemStack(@NotNull ItemStack itemStack) {
        this.itemStack = cloneItem(itemStack);
    }

    private static ItemStack cloneItem(ItemStack itemStack) {
        return Objects.requireNonNull(itemStack, "itemStack").clone();
    }
}
