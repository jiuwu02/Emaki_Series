package emaki.jiuwu.craft.level.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import emaki.jiuwu.craft.level.api.LevelUpCause;

/**
 * Fired by EmakiLevel after a player has successfully gained a level.
 *
 * <p>This event is informational: the level has already been applied, rewards
 * and success actions have run, and the new level cannot be reverted by
 * listeners. It is suitable for achievements, announcements, particle effects
 * and other downstream reactions.
 *
 * <p><strong>Thread:</strong> synchronously on the player's owner thread. Player data persistence may
 * complete asynchronously after this event.
 */
public final class PlayerLevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String typeId;
    private final int oldLevel;
    private final int newLevel;
    private final LevelUpCause cause;

    /**
     * Creates a level up event.
     *
     * @param player   the player who levelled up, may be {@code null} when
     *                 offline
     * @param typeId   the level type id
     * @param oldLevel the level before the upgrade
     * @param newLevel the level after the upgrade
     * @param cause    the cause that triggered the upgrade, may be {@code null}
     */
    public PlayerLevelUpEvent(Player player,
            String typeId,
            int oldLevel,
            int newLevel,
            LevelUpCause cause) {
        this.player = player;
        this.typeId = typeId;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.cause = cause;
    }

    /** {@return the player who levelled up, or {@code null} if offline} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the level type id this upgrade applies to} */
    public String getTypeId() {
        return typeId;
    }

    /** {@return the level before the upgrade} */
    public int getOldLevel() {
        return oldLevel;
    }

    /** {@return the level after the upgrade} */
    public int getNewLevel() {
        return newLevel;
    }

    /** {@return the cause that triggered the upgrade, or {@code null}} */
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
