package emaki.jiuwu.craft.accessory.api.event;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiAccessory when the equipped piece count of an accessory set changes for a player.
 *
 * <p>This is an edge-triggered notification: the accessory contribution snapshot is recomputed on
 * every GUI change, data load and reload, but this event only fires when a set's equipped piece count
 * actually changes. The set bonus has already been folded into the attribute and skill contributions
 * by the time listeners run, so the event is informational and cannot be cancelled.
 *
 * <p>Fired on the player's entity-owner thread. Orphaned accessory slots never count toward a piece
 * total, so removing a part from the configuration can lower the count reported here. Accessory sets
 * only count items inside accessory slots; they never mix with EmakiItem equipment sets.
 */
public final class AccessorySetBonusChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String setId;
    private final int oldPieceCount;
    private final int newPieceCount;
    private final int totalPieces;
    private final List<Integer> activeThresholds;

    /**
     * Creates an accessory set bonus change event.
     *
     * @param player           the player whose accessory set state changed
     * @param setId            the accessory set id
     * @param oldPieceCount    the equipped piece count before the change
     * @param newPieceCount    the equipped piece count after the change
     * @param totalPieces      the total number of pieces declared by the set
     * @param activeThresholds the required-piece numbers of the now-active thresholds
     */
    public AccessorySetBonusChangeEvent(Player player,
            String setId,
            int oldPieceCount,
            int newPieceCount,
            int totalPieces,
            List<Integer> activeThresholds) {
        this.player = player;
        this.setId = setId;
        this.oldPieceCount = oldPieceCount;
        this.newPieceCount = newPieceCount;
        this.totalPieces = totalPieces;
        this.activeThresholds = activeThresholds == null ? List.of() : List.copyOf(activeThresholds);
    }

    /** {@return the player whose accessory set state changed} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the accessory set id} */
    public String getSetId() {
        return setId;
    }

    /** {@return the equipped piece count before the change} */
    public int getOldPieceCount() {
        return oldPieceCount;
    }

    /** {@return the equipped piece count after the change} */
    public int getNewPieceCount() {
        return newPieceCount;
    }

    /** {@return the total number of pieces declared by the set} */
    public int getTotalPieces() {
        return totalPieces;
    }

    /** {@return the required-piece numbers of the now-active thresholds} */
    public List<Integer> getActiveThresholds() {
        return activeThresholds;
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
