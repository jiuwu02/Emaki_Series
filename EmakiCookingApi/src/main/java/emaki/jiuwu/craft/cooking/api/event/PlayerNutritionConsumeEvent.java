package emaki.jiuwu.craft.cooking.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired after a consumed item source is resolved and before matching nutrition rules are applied.
 *
 * <p>Runs synchronously on the player's owner thread and covers vanilla, MMOItems and NeigeItems consume
 * channels. Cancellation suppresses both nutrition changes and food-source actions. The item accessor is a
 * live reference; in-place mutations are visible to the rule matching that follows this event.
 */
public final class PlayerNutritionConsumeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack item;
    private final String itemSource;
    private boolean cancelled;

    /**
     * Creates a nutrition consume event.
     *
     * @param player     the consuming player
     * @param item       the item being consumed
     * @param itemSource the resolved item source shorthand
     */
    public PlayerNutritionConsumeEvent(Player player, ItemStack item, String itemSource) {
        this.player = player;
        this.item = item;
        this.itemSource = itemSource;
    }

    /** {@return the consuming player} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the item being consumed} */
    public ItemStack getItem() {
        return item;
    }

    /** {@return the resolved item source shorthand} */
    public String getItemSource() {
        return itemSource;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
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
