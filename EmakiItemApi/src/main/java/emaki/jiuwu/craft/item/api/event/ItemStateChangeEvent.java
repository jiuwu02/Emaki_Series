package emaki.jiuwu.craft.item.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.item.api.ItemStateKey;
import emaki.jiuwu.craft.item.api.ItemStateMutation;

/** Fired after a committed item-state mutation. */
public final class ItemStateChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ItemStack item;
    private final ItemStateMutation<?> mutation;
    private final Player holder;

    public ItemStateChangeEvent(ItemStack item, ItemStateMutation<?> mutation) {
        this(item, mutation, null);
    }

    /**
     * Creates an event that also names the player holding the mutated stack.
     *
     * @param item the mutated stack, copied defensively
     * @param mutation the committed mutation result
     * @param holder the holding player, or {@code null} when the mutation had no known holder
     */
    public ItemStateChangeEvent(ItemStack item, ItemStateMutation<?> mutation, @Nullable Player holder) {
        this.item = item == null ? null : item.clone();
        this.mutation = mutation;
        this.holder = holder;
    }

    public ItemStack getItem() { return item == null ? null : item.clone(); }
    public ItemStateMutation<?> getMutation() { return mutation; }

    /**
     * {@return the player that held the stack when the mutation ran, or {@code null}}
     *
     * <p>{@code null} means the holder was unknown to the mutating call site, not that the stack
     * is unheld.
     */
    public @Nullable Player getHolder() { return holder; }
    public ItemStateKey<?> getKey() { return mutation == null ? null : mutation.key(); }
    public Object getOldValue() { return mutation == null ? null : mutation.oldValue(); }
    public Object getNewValue() { return mutation == null ? null : mutation.newValue(); }
    public Number getDelta() { return mutation == null ? null : mutation.delta(); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
