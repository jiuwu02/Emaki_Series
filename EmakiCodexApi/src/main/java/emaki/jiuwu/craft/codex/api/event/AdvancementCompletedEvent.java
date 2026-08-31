package emaki.jiuwu.craft.codex.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired from Bukkit's actual completion callback after a registered advancement criterion is awarded.
 *
 * <p>Runs synchronously on the player's owner thread and covers both EmakiCodex grants and awards made by
 * other sources. It is informational: completion actions are already queued, cancellation is unavailable,
 * and re-granting an already-complete advancement does not fire it again.
 *
 * @see AdvancementGrantEvent
 */
public final class AdvancementCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String advancementId;
    private final String advancementKey;

    /**
     * Creates an advancement completed event.
     *
     * @param player         the player who received the advancement
     * @param advancementId  the advancement id as supplied by the caller
     * @param advancementKey the resolved fully qualified advancement key
     */
    public AdvancementCompletedEvent(Player player, String advancementId, String advancementKey) {
        this.player = player;
        this.advancementId = advancementId;
        this.advancementKey = advancementKey;
    }

    /** {@return the player who received the advancement} */
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
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    /** {@return the shared handler list for this event type} */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
