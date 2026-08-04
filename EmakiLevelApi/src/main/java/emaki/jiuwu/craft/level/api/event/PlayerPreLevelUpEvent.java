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
 * <p>This is the pre-commit hook, not a completion notification: nothing has been charged or written
 * when listeners run. It is cancellable, and cancelling prevents the level-up and leaves the journal,
 * balances, inventory, level state, rewards and actions untouched. The runtime then reports the
 * attempt as a {@code CANCELLED} failure. Cancelling is the only supported way to veto an upgrade;
 * the reported levels and requirement are read-only.
 *
 * <h2>Threading</h2>
 * Fired synchronously on the thread that owns the levelling player, inside the runtime's player-data
 * mutation, so listeners may safely touch the player, their inventory and the surrounding world.
 * Because the mutation is held open for the duration of the call, listeners must return quickly and
 * must not block on other threads or on futures. Player data may be loaded or saved asynchronously
 * afterwards, but this event does not signal persistence completion.
 *
 * <h2>Coverage — this event is not fired for every level-up attempt</h2>
 * It is fired only when the player is online and the current thread owns them, so upgrades resolved
 * for a player the runtime does not own are skipped. It is also skipped when an earlier check already
 * failed: upgrading disabled for the type, manual upgrading disabled, the player already at max
 * level, no valid requirement for the next level, or insufficient experience. Conversely it fires for
 * every cause, including automatic upgrades chained from an experience gain, so a single
 * {@code addExp} call may fire it repeatedly. Treat a missing event as "no committed upgrade to veto"
 * rather than "no attempt happened", and do not use it as an audit trail; observe
 * {@link PlayerLevelUpEvent} for committed upgrades.
 *
 * @see PlayerLevelUpEvent
 * @see PlayerMaxLevelReachedEvent
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

    /**
     * Creates a pre level-up event.
     *
     * <p>Constructed by EmakiLevel; third-party code normally only listens for this event.
     *
     * @param player       the player about to level up; the runtime only constructs this event for an
     *                     online, thread-owned player
     * @param typeId       the level type id being upgraded
     * @param currentLevel the level before the pending upgrade
     * @param targetLevel  the level that would be reached, always one above {@code currentLevel}
     * @param requiredExp  the experience the upgrade would consume
     * @param cause        the attribution for the pending upgrade, may be {@code null}
     */
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

    /** {@return the player about to level up, online and owned by the firing thread} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the level type id being upgraded} */
    public String getTypeId() {
        return typeId;
    }

    /** {@return the level before the pending upgrade} */
    public int getCurrentLevel() {
        return currentLevel;
    }

    /**
     * {@return the level that would be reached, always one above {@link #getCurrentLevel()}}
     *
     * <p>A single upgrade step never skips levels; chained automatic upgrades fire this event once per
     * step.
     */
    public int getTargetLevel() {
        return targetLevel;
    }

    /**
     * {@return the experience this upgrade would consume}
     *
     * <p>The single-step requirement for {@link #getTargetLevel()}, already verified to be positive
     * and covered by the player's current progress. It is deducted only if the event is not cancelled.
     */
    public double getRequiredExp() {
        return requiredExp;
    }

    /**
     * {@return the attribution for the pending upgrade, or {@code null}}
     *
     * <p>{@link LevelUpCause#AUTO} identifies an upgrade chained from an experience gain rather than a
     * direct request.
     */
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

    /** {@return the shared handler list for this event type} */
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
