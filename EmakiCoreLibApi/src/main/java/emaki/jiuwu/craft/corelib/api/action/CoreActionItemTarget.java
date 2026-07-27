package emaki.jiuwu.craft.corelib.api.action;

import java.util.Objects;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Mutable item holder for CoreLib action chains that need to return a replacement stack.
 *
 * <p>Callers place one instance in a {@link CoreActionContext} attribute using
 * {@link #ATTRIBUTE_KEY}, wait for the action chain to complete, then read the latest
 * stack and perform the final inventory, GUI, or transaction commit themselves.</p>
 *
 * <p>The holder clones every value crossing its boundary. Its volatile reference makes
 * completed action-stage writes visible to the thread that later reads the result.</p>
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
