package emaki.jiuwu.craft.gem.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired by EmakiGem after an inlay has been fully committed.
 *
 * <p>Informational only: the gem layer is written, costs are charged, success actions have run, and the
 * operation journal entry is closed. Cancelling is therefore impossible.
 *
 * <h2>Threading</h2>
 * Fired synchronously on the thread that owns the actor.
 *
 * <h2>Coverage</h2>
 * Fired only by {@code GemOperations.inlay}. EmakiGem's GUI and held-item action paths do not fire it,
 * so this is not an exhaustive audit trail of every inlay on the server.
 *
 * @see GemInlayEvent
 */
public final class GemInlayCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player actor;
    private final ItemStack equipment;
    private final int slotIndex;
    private final String gemId;
    private final int gemLevel;
    private final boolean inputConsumed;

    /**
     * Creates an inlay completed event.
     *
     * @param actor         the player who performed the inlay
     * @param equipment     the equipment after the gem was inlaid
     * @param slotIndex     the socket slot the gem was placed into
     * @param gemId         the gem definition id
     * @param gemLevel      the gem level
     * @param inputConsumed whether the supplied gem item was consumed
     */
    public GemInlayCompletedEvent(Player actor,
            ItemStack equipment,
            int slotIndex,
            String gemId,
            int gemLevel,
            boolean inputConsumed) {
        this.actor = actor;
        this.equipment = equipment;
        this.slotIndex = slotIndex;
        this.gemId = gemId;
        this.gemLevel = gemLevel;
        this.inputConsumed = inputConsumed;
    }

    /** {@return the player who performed the inlay} */
    public Player getActor() {
        return actor;
    }

    /** {@return the equipment after the gem was inlaid} */
    public ItemStack getEquipment() {
        return equipment;
    }

    /** {@return the socket slot the gem was placed into} */
    public int getSlotIndex() {
        return slotIndex;
    }

    /** {@return the gem definition id} */
    public String getGemId() {
        return gemId;
    }

    /** {@return the gem level} */
    public int getGemLevel() {
        return gemLevel;
    }

    /** {@return whether the supplied gem item was consumed} */
    public boolean isInputConsumed() {
        return inputConsumed;
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
