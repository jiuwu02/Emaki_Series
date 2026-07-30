package emaki.jiuwu.craft.codex.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiCodex before an advancement is granted to a player.
 *
 * <p>Cancelling stops the grant: the player's advancement progress is left untouched and the calling
 * operation reports a rejection.
 *
 * <h2>Threading</h2>
 * Fired synchronously on the thread that owns the target player. EmakiCodex verifies ownership before
 * attempting the write, so an off-thread grant is refused before this event would fire.
 *
 * <h2>Coverage</h2>
 * Fired by every EmakiCodex mutation path after validation and before criterion award: public API calls,
 * commands, configured actions, configured gameplay triggers, and external trigger providers. Direct
 * criterion awards performed outside EmakiCodex do not fire this pre-event.
 *
 * @see AdvancementCompletedEvent
 */
public final class AdvancementGrantEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String advancementId;
    private final String advancementKey;
    private boolean cancelled;

    /**
     * Creates an advancement grant event.
     *
     * @param player         the player receiving the advancement
     * @param advancementId  the advancement id as supplied by the caller
     * @param advancementKey the resolved fully qualified advancement key
     */
    public AdvancementGrantEvent(Player player, String advancementId, String advancementKey) {
        this.player = player;
        this.advancementId = advancementId;
        this.advancementKey = advancementKey;
    }

    /** {@return the player receiving the advancement} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the advancement id as supplied by the caller} */
    public String getAdvancementId() {
        return advancementId;
    }

    /** {@return the resolved fully qualified advancement key} */
    public String getAdvancementKey() {
        return advancementKey;
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
