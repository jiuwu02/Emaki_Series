package emaki.jiuwu.craft.codex.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after validation and before EmakiCodex awards an advancement criterion.
 *
 * <p>Runs synchronously on the player's owner thread for public API, command, configured-action and trigger
 * paths. Direct awards outside EmakiCodex do not fire it. Cancellation leaves progress untouched and the
 * initiating operation reports cancellation.
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
