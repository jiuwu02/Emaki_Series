package emaki.jiuwu.craft.level.api.event;

import emaki.jiuwu.craft.level.api.LevelUpCause;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiLevel when a player reaches the maximum level of a level type
 * for the first time through an experience-driven level up.
 *
 * <p>This is an informational milestone event: it fires once, on the level up
 * that takes the player to the configured max level. It is suitable for
 * announcements, achievements and capstone rewards. Listeners cannot revert the level.
 *
 * <p><strong>Thread:</strong> synchronously on the player's owner thread. Player data persistence may
 * complete asynchronously after this event.
 */
public final class PlayerMaxLevelReachedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String typeId;
    private final int maxLevel;
    private final LevelUpCause cause;

    /**
     * Creates a max-level-reached event.
     *
     * @param player   the player who reached max level, may be {@code null}
     * @param typeId   the level type id
     * @param maxLevel the configured maximum level
     * @param cause    the cause of the level up, may be {@code null}
     */
    public PlayerMaxLevelReachedEvent(Player player, String typeId, int maxLevel, LevelUpCause cause) {
        this.player = player;
        this.typeId = typeId;
        this.maxLevel = maxLevel;
        this.cause = cause;
    }

    /** {@return the player who reached max level, or {@code null} if offline} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the level type id} */
    public String getTypeId() {
        return typeId;
    }

    /** {@return the configured maximum level} */
    public int getMaxLevel() {
        return maxLevel;
    }

    /** {@return the cause of the level up, or {@code null}} */
    public LevelUpCause getCause() {
        return cause;
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
