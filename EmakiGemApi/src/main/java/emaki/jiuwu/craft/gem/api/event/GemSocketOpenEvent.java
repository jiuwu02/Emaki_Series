package emaki.jiuwu.craft.gem.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired by EmakiGem after a socket-open request has resolved a target slot but
 * before the slot is opened and the opener item is consumed.
 *
 * <p>Listeners may inspect the actor, the equipment, the opener item/id and the
 * resolved slot index, or cancel the open entirely. A cancelled event stops
 * EmakiGem from opening the socket and consuming the opener. This event is
 * fired on the server thread.
 */
public final class GemSocketOpenEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player actor;
    private final ItemStack equipment;
    private final ItemStack openerItem;
    private final String openerId;
    private final int slotIndex;
    private final String itemDefinitionId;
    private boolean cancelled;

    /**
     * Creates a socket-open event.
     *
     * @param actor            the player performing the open
     * @param equipment        the equipment receiving the new socket
     * @param openerItem       the opener item being used, may be {@code null}
     * @param openerId         the opener config id
     * @param slotIndex        the resolved socket slot index
     * @param itemDefinitionId the gem item definition id of the equipment
     */
    public GemSocketOpenEvent(Player actor,
            ItemStack equipment,
            ItemStack openerItem,
            String openerId,
            int slotIndex,
            String itemDefinitionId) {
        this.actor = actor;
        this.equipment = equipment;
        this.openerItem = openerItem;
        this.openerId = openerId;
        this.slotIndex = slotIndex;
        this.itemDefinitionId = itemDefinitionId;
    }

    /** {@return the player performing the open} */
    public Player getActor() {
        return actor;
    }

    /** {@return the equipment receiving the new socket} */
    public ItemStack getEquipment() {
        return equipment;
    }

    /** {@return the opener item being used, or {@code null}} */
    public ItemStack getOpenerItem() {
        return openerItem;
    }

    /** {@return the opener config id} */
    public String getOpenerId() {
        return openerId;
    }

    /** {@return the resolved socket slot index} */
    public int getSlotIndex() {
        return slotIndex;
    }

    /** {@return the gem item definition id of the equipment} */
    public String getItemDefinitionId() {
        return itemDefinitionId;
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
