package emaki.jiuwu.craft.item.api.event;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiItem when the number of active pieces of an equipped set
 * changes for a player.
 *
 * <p>This is an edge-triggered notification: EmakiItem recomputes equipped sets
 * on inventory changes, but this event only fires when a set's active piece
 * count actually changes (pieces equipped or unequipped). The set bonus has
 * already been applied; this event is purely informational and cannot be
 * cancelled. It is suitable for achievements, custom buffs and UI updates. This
 * event is fired on the player's entity-owner thread.
 */
public final class ItemSetBonusChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String setId;
    private final int oldActiveCount;
    private final int newActiveCount;
    private final int totalPieces;
    private final List<Integer> activeThresholds;
    private final String trigger;

    /**
     * Creates a set bonus change event.
     *
     * @param player           the player whose set state changed
     * @param setId            the item set id
     * @param oldActiveCount   the active piece count before the change
     * @param newActiveCount   the active piece count after the change
     * @param totalPieces      the total number of pieces in the set
     * @param activeThresholds the required-piece numbers of the now-active
     *                         thresholds
     * @param trigger          the trigger that caused the refresh
     */
    public ItemSetBonusChangeEvent(Player player,
            String setId,
            int oldActiveCount,
            int newActiveCount,
            int totalPieces,
            List<Integer> activeThresholds,
            String trigger) {
        this.player = player;
        this.setId = setId;
        this.oldActiveCount = oldActiveCount;
        this.newActiveCount = newActiveCount;
        this.totalPieces = totalPieces;
        this.activeThresholds = activeThresholds == null ? List.of() : List.copyOf(activeThresholds);
        this.trigger = trigger;
    }

    /** {@return the player whose set state changed} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the item set id} */
    public String getSetId() {
        return setId;
    }

    /** {@return the active piece count before the change} */
    public int getOldActiveCount() {
        return oldActiveCount;
    }

    /** {@return the active piece count after the change} */
    public int getNewActiveCount() {
        return newActiveCount;
    }

    /** {@return the total number of pieces in the set} */
    public int getTotalPieces() {
        return totalPieces;
    }

    /** {@return the required-piece numbers of the now-active thresholds} */
    public List<Integer> getActiveThresholds() {
        return activeThresholds;
    }

    /** {@return the trigger that caused the refresh} */
    public String getTrigger() {
        return trigger;
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
