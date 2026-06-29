package emaki.jiuwu.craft.item.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired by EmakiItem after a custom item has been created from a definition id
 * but before it is returned to the caller.
 *
 * <p>This event is informational and mutable: the item already exists, but
 * listeners may replace the produced item via {@link #setResult(ItemStack)}
 * (for example to stamp extra data from another plugin). It is not cancellable.
 *
 * <p>Item creation may originate from the API or commands without an online
 * player, so {@link #getPlayer()} may return {@code null}; listeners must
 * null-check it. This event is fired on the server thread.
 */
public final class EmakiItemCreateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String id;
    private final int amount;
    private final Player player;
    private ItemStack result;

    /**
     * Creates an item creation event.
     *
     * @param id     the item definition id
     * @param amount the resolved item amount
     * @param player the player the item is created for, may be {@code null}
     * @param result the produced item
     */
    public EmakiItemCreateEvent(String id, int amount, Player player, ItemStack result) {
        this.id = id;
        this.amount = amount;
        this.player = player;
        this.result = result;
    }

    /** {@return the item definition id} */
    public String getId() {
        return id;
    }

    /** {@return the resolved item amount} */
    public int getAmount() {
        return amount;
    }

    /** {@return the player the item is created for, or {@code null}} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the produced item, possibly replaced by a listener} */
    public ItemStack getResult() {
        return result;
    }

    /**
     * Replaces the produced item returned to the caller.
     *
     * @param result the new item; {@code null} is ignored by EmakiItem
     */
    public void setResult(ItemStack result) {
        this.result = result;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** {@return the shared handler list for this event type} */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
