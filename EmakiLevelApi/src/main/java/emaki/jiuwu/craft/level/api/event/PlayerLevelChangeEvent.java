package emaki.jiuwu.craft.level.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import emaki.jiuwu.craft.level.api.LevelOperationType;

/**
 * Fired by EmakiLevel after a player's level has been changed through an
 * administrative operation (add/remove/set level, or reset).
 *
 * <p>Unlike {@link PlayerLevelUpEvent}, which only covers experience-driven
 * level ups, this event covers direct level changes from commands and the API.
 * The change has already been applied and cannot be reverted by listeners. It
 * carries both the old and new level so listeners can determine the direction.
 * This event is fired on the server thread.
 */
public final class PlayerLevelChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String typeId;
    private final int oldLevel;
    private final int newLevel;
    private final LevelOperationType operationType;

    /**
     * Creates a level change event.
     *
     * @param player        the affected player, may be {@code null} when offline
     * @param typeId        the level type id
     * @param oldLevel      the level before the change
     * @param newLevel      the level after the change
     * @param operationType the operation that caused the change
     */
    public PlayerLevelChangeEvent(Player player,
            String typeId,
            int oldLevel,
            int newLevel,
            LevelOperationType operationType) {
        this.player = player;
        this.typeId = typeId;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.operationType = operationType;
    }

    /** {@return the affected player, or {@code null} if offline} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the level type id this change applies to} */
    public String getTypeId() {
        return typeId;
    }

    /** {@return the level before the change} */
    public int getOldLevel() {
        return oldLevel;
    }

    /** {@return the level after the change} */
    public int getNewLevel() {
        return newLevel;
    }

    /** {@return the operation that caused the change} */
    public LevelOperationType getOperationType() {
        return operationType;
    }

    /** {@return whether the level increased} */
    public boolean isIncrease() {
        return newLevel > oldLevel;
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
