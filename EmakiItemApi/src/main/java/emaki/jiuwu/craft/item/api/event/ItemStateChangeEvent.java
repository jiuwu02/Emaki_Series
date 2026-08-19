package emaki.jiuwu.craft.item.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.item.api.ItemStateKey;
import emaki.jiuwu.craft.item.api.ItemStateMutation;

/** Fired after a committed item-state mutation. */
public final class ItemStateChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final ItemStack item;
    private final ItemStateMutation<?> mutation;

    public ItemStateChangeEvent(ItemStack item, ItemStateMutation<?> mutation) {
        this.item = item == null ? null : item.clone();
        this.mutation = mutation;
    }

    public ItemStack getItem() { return item == null ? null : item.clone(); }
    public ItemStateMutation<?> getMutation() { return mutation; }
    public ItemStateKey<?> getKey() { return mutation == null ? null : mutation.key(); }
    public Object getOldValue() { return mutation == null ? null : mutation.oldValue(); }
    public Object getNewValue() { return mutation == null ? null : mutation.newValue(); }
    public Number getDelta() { return mutation == null ? null : mutation.delta(); }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
