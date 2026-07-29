package emaki.jiuwu.craft.gem.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired by EmakiGem after an extraction has been fully committed.
 *
 * <p>Informational only: the gem layer is written, costs are charged, success actions have run, and the
 * operation journal entry is closed.
 *
 * <p>{@link #getReturnedGem()} may be {@code null}: the configured return mode can destroy the gem
 * outright, and a degraded return can hand back a lower level than was inlaid.
 *
 * <h2>Threading</h2>
 * Fired synchronously on the thread that owns the actor.
 *
 * <h2>Coverage</h2>
 * Fired only by {@code GemOperations.extract}. EmakiGem's GUI and held-item action paths do not fire it.
 *
 * @see GemExtractEvent
 */
public final class GemExtractCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player actor;
    private final ItemStack equipment;
    private final ItemStack returnedGem;
    private final int slotIndex;
    private final String gemId;
    private final int gemLevel;
    private final String returnMode;

    /**
     * Creates an extraction completed event.
     *
     * @param actor       the player who performed the extraction
     * @param equipment   the equipment after the gem was removed
     * @param returnedGem the gem item handed back, or {@code null} when the return mode destroyed it
     * @param slotIndex   the socket slot the gem was taken from
     * @param gemId       the gem definition id
     * @param gemLevel    the gem level before any degradation
     * @param returnMode  the configured return mode that produced this outcome
     */
    public GemExtractCompletedEvent(Player actor,
            ItemStack equipment,
            ItemStack returnedGem,
            int slotIndex,
            String gemId,
            int gemLevel,
            String returnMode) {
        this.actor = actor;
        this.equipment = equipment;
        this.returnedGem = returnedGem;
        this.slotIndex = slotIndex;
        this.gemId = gemId;
        this.gemLevel = gemLevel;
        this.returnMode = returnMode;
    }

    /** {@return the player who performed the extraction} */
    public Player getActor() {
        return actor;
    }

    /** {@return the equipment after the gem was removed} */
    public ItemStack getEquipment() {
        return equipment;
    }

    /** {@return the gem item handed back, or {@code null} when the return mode destroyed it} */
    public ItemStack getReturnedGem() {
        return returnedGem;
    }

    /** {@return the socket slot the gem was taken from} */
    public int getSlotIndex() {
        return slotIndex;
    }

    /** {@return the gem definition id} */
    public String getGemId() {
        return gemId;
    }

    /** {@return the gem level before any degradation} */
    public int getGemLevel() {
        return gemLevel;
    }

    /** {@return the configured return mode that produced this outcome} */
    public String getReturnMode() {
        return returnMode;
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
