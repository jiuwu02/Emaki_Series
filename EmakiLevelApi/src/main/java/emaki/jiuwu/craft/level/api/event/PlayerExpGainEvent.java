package emaki.jiuwu.craft.level.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiLevel before experience is written to a player's level entry.
 *
 * <p>The amount carried by this event is the value <em>after</em> EmakiLevel's
 * own experience rules (multipliers, daily caps) have been applied, but before
 * it is added to the player. Listeners may inspect the source player, level
 * type and current progress, adjust the gained amount via
 * {@link #setAmount(double)}, or cancel the gain entirely.
 *
 * <p>Cancelling the event (or setting an amount {@code <= 0}) prevents
 * EmakiLevel from adding any experience for this call. This event is fired on
 * the server thread.
 */
public final class PlayerExpGainEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String typeId;
    private final int currentLevel;
    private final double currentExp;
    private final String reason;
    private double amount;
    private boolean cancelled;

    /**
     * Creates an experience gain event.
     *
     * @param player       the player gaining experience, may be {@code null}
     *                     when no online player backs the operation
     * @param typeId       the level type id
     * @param currentLevel the player's current level before this gain
     * @param currentExp   the player's current experience before this gain
     * @param amount       the experience that will be added unless changed
     * @param reason       the reason tag describing the source of the gain
     */
    public PlayerExpGainEvent(Player player,
            String typeId,
            int currentLevel,
            double currentExp,
            double amount,
            String reason) {
        this.player = player;
        this.typeId = typeId;
        this.currentLevel = currentLevel;
        this.currentExp = currentExp;
        this.amount = amount;
        this.reason = reason;
    }

    /** {@return the player gaining experience, or {@code null} if offline} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the level type id this gain applies to} */
    public String getTypeId() {
        return typeId;
    }

    /** {@return the player's level before this gain} */
    public int getCurrentLevel() {
        return currentLevel;
    }

    /** {@return the player's experience before this gain} */
    public double getCurrentExp() {
        return currentExp;
    }

    /** {@return the reason tag describing the source of this gain} */
    public String getReason() {
        return reason;
    }

    /** {@return the experience that will be added unless changed or cancelled} */
    public double getAmount() {
        return amount;
    }

    /**
     * Overrides the experience that will be added after the event.
     *
     * @param amount the new amount; values {@code <= 0} suppress the gain
     */
    public void setAmount(double amount) {
        this.amount = amount;
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
