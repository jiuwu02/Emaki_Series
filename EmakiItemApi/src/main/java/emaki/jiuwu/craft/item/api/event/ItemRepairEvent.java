package emaki.jiuwu.craft.item.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired by EmakiItem after a repair request has passed validation but before
 * any cost is charged and the durability is restored.
 *
 * <p>Listeners may inspect the player, the equipment, the repair source and the
 * current/max damage, adjust the restored amount via
 * {@link #setRestoreAmount(int)}, or cancel the repair entirely. A cancelled
 * event stops EmakiItem from charging and restoring durability. This event is
 * fired on the repaired player's entity-owner thread.
 */
public final class ItemRepairEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack equipment;
    private final String itemId;
    private final String source;
    private final int currentDamage;
    private final int maxDamage;
    private int restoreAmount;
    private boolean cancelled;

    /**
     * Creates a repair event.
     *
     * @param player        the player performing the repair
     * @param equipment     the equipment being repaired
     * @param itemId        the EmakiItem definition id
     * @param source        the repair source (material/economy)
     * @param restoreAmount the durability that will be restored unless changed
     * @param currentDamage the equipment's current damage before the repair
     * @param maxDamage     the equipment's maximum damage
     */
    public ItemRepairEvent(Player player,
            ItemStack equipment,
            String itemId,
            String source,
            int restoreAmount,
            int currentDamage,
            int maxDamage) {
        this.player = player;
        this.equipment = equipment;
        this.itemId = itemId;
        this.source = source;
        this.restoreAmount = restoreAmount;
        this.currentDamage = currentDamage;
        this.maxDamage = maxDamage;
    }

    /** {@return the player performing the repair} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the equipment being repaired} */
    public ItemStack getEquipment() {
        return equipment;
    }

    /** {@return the EmakiItem definition id} */
    public String getItemId() {
        return itemId;
    }

    /** {@return the repair source (material/economy)} */
    public String getSource() {
        return source;
    }

    /** {@return the equipment's current damage before the repair} */
    public int getCurrentDamage() {
        return currentDamage;
    }

    /** {@return the equipment's maximum damage} */
    public int getMaxDamage() {
        return maxDamage;
    }

    /** {@return the durability that will be restored unless changed or cancelled} */
    public int getRestoreAmount() {
        return restoreAmount;
    }

    /**
     * Overrides the durability that will be restored after the event.
     *
     * @param restoreAmount the new restore amount; values {@code <= 0} suppress
     *                      the repair
     */
    public void setRestoreAmount(int restoreAmount) {
        this.restoreAmount = restoreAmount;
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
