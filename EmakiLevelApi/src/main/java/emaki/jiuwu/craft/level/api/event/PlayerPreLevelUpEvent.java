package emaki.jiuwu.craft.level.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import emaki.jiuwu.craft.level.api.LevelUpCause;

/**
 * Fired after all non-mutating level-up checks pass and immediately before EmakiLevel starts its
 * journal, charges costs, changes state, grants rewards, runs actions, or fires post events.
 *
 * <p><strong>Thread:</strong> synchronously on the player's owner thread. Cancelling prevents the
 * level-up and leaves the journal, balances, inventory, level state, rewards and actions untouched.
 * Player data may be loaded or saved asynchronously, but this event does not signal persistence
 * completion.
 */
public final class PlayerPreLevelUpEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String typeId;
    private final int currentLevel;
    private final int targetLevel;
    private final double requiredExp;
    private final LevelUpCause cause;
    private boolean cancelled;

    public PlayerPreLevelUpEvent(Player player,
            String typeId,
            int currentLevel,
            int targetLevel,
            double requiredExp,
            LevelUpCause cause) {
        this.player = player;
        this.typeId = typeId;
        this.currentLevel = currentLevel;
        this.targetLevel = targetLevel;
        this.requiredExp = requiredExp;
        this.cause = cause;
    }

    public Player getPlayer() {
        return player;
    }

    public String getTypeId() {
        return typeId;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public int getTargetLevel() {
        return targetLevel;
    }

    public double getRequiredExp() {
        return requiredExp;
    }

    public LevelUpCause getCause() {
        return cause;
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

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
