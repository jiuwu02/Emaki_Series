package emaki.jiuwu.craft.codex.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiCodex after an advancement has been granted to a player.
 *
 * <p>Informational only: the advancement criterion is already awarded and any configured completion actions
 * have been queued, so this event is not cancellable.
 *
 * <h2>Threading</h2>
 * Fired synchronously on the thread that owns the target player.
 *
 * <h2>Coverage</h2>
 * Fired from Bukkit's actual player advancement-completion event for every advancement registered by
 * EmakiCodex. It therefore covers EmakiCodex grant paths and criterion awards performed by other sources.
 * Re-granting an already-completed advancement does not produce another completion event.
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
