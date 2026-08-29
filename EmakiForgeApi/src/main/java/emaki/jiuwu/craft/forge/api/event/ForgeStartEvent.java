package emaki.jiuwu.craft.forge.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus;

/**
 * Fired by EmakiForge when a player confirms a forge attempt, just before the asynchronous forge
 * execution begins.
 *
 * <p>Cancelling this event stops the attempt: EmakiForge returns without scheduling execution and
 * without consuming materials.
 *

 * Fired synchronously on the thread that owns the forging player. On Paper that is the main thread;
 * on Folia it is the player's region thread. EmakiForge verifies ownership before firing, so if the
 * owner thread is unavailable the attempt is abandoned and <em>this event is not fired at all</em>.
 *

 * Both the GUI confirmation path and
 * {@link emaki.jiuwu.craft.forge.api.ForgeOperations#forgeAsync} fire this event. Listeners may veto
 * the attempt or adjust its success-rate roll through {@link #setSuccessRate(double)}. Quality tier
 * selection and item assembly remain controlled by EmakiForge.
 *
 * @see ForgeCompletedEvent
 */
public final class ForgeStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String recipeId;
    private final boolean firstCraft;
    private double successRate;
    private boolean cancelled;

    /**
     * Creates a forge start event.
     *
     * @param player      the player performing the forge
     * @param recipeId    the recipe id being forged
     * @param firstCraft  whether this is the player's first craft of the recipe
     * @param successRate initial recipe success rate in percent (0-100)
     */
    public ForgeStartEvent(Player player, String recipeId, boolean firstCraft, double successRate) {
        this.player = player;
        this.recipeId = recipeId;
        this.firstCraft = firstCraft;
        setSuccessRate(successRate);
    }

    /** {@return the player performing the forge} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the recipe id being forged} */
    public String getRecipeId() {
        return recipeId;
    }

    /** {@return whether this is the player's first craft of the recipe} */
    public boolean isFirstCraft() {
        return firstCraft;
    }

    /** {@return the effective success rate in percent (0-100)} */
    public double getSuccessRate() {
        return successRate;
    }

    /**
     * Replaces the success rate used by this attempt's chance roll.
     *
     * <p>Finite values are clamped to {@code [0, 100]}. Non-finite values become {@code 0} so an
     * invalid listener value can never turn into an implicit guaranteed success.
     *
     * @param successRate effective success rate in percent
     */
    @ApiStatus.Experimental
    public void setSuccessRate(double successRate) {
        this.successRate = Double.isFinite(successRate)
                ? Math.max(0D, Math.min(100D, successRate))
                : 0D;
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
