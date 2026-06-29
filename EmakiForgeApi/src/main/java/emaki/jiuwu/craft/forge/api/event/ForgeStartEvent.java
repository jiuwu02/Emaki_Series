package emaki.jiuwu.craft.forge.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiForge when a player confirms a forge attempt, just before the
 * asynchronous forge execution begins.
 *
 * <p>This is the only point in the forge flow that runs on the server thread
 * before the async chain starts, so it is the place to cancel a forge. A
 * cancelled event stops EmakiForge from running the attempt. The success rate
 * carried here is the configured recipe value; the actual roll may be adjusted
 * by JavaScript rules inside the async chain, so it is exposed read-only.
 *
 * <p>This event is fired on the server thread.
 */
public final class ForgeStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String recipeId;
    private final boolean firstCraft;
    private final double successRate;
    private boolean cancelled;

    /**
     * Creates a forge start event.
     *
     * @param player      the player performing the forge
     * @param recipeId    the recipe id being forged
     * @param firstCraft  whether this is the player's first craft of the recipe
     * @param successRate the configured recipe success rate in percent (0-100),
     *                    read-only
     */
    public ForgeStartEvent(Player player, String recipeId, boolean firstCraft, double successRate) {
        this.player = player;
        this.recipeId = recipeId;
        this.firstCraft = firstCraft;
        this.successRate = successRate;
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

    /** {@return the configured recipe success rate in percent (0-100)} */
    public double getSuccessRate() {
        return successRate;
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
