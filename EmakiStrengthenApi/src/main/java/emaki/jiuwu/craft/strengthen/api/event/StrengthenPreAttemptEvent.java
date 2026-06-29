package emaki.jiuwu.craft.strengthen.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired by EmakiStrengthen after a strengthen attempt has been previewed and
 * passed its condition checks, but before the success roll and before any cost
 * is charged.
 *
 * <p>Listeners may inspect the player, the target item, the recipe and the
 * current/target stars, override the success rate via
 * {@link #setSuccessRate(double)}, or cancel the attempt entirely. A cancelled
 * event stops EmakiStrengthen from rolling, rebuilding the item or charging any
 * cost. This event is fired on the server thread.
 */
public final class StrengthenPreAttemptEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack targetItem;
    private final String recipeId;
    private final int currentStar;
    private final int targetStar;
    private double successRate;
    private boolean cancelled;

    /**
     * Creates a pre-attempt event.
     *
     * @param player      the player performing the attempt
     * @param targetItem  the item being strengthened, may be {@code null}
     * @param recipeId    the strengthen recipe id, may be {@code null}
     * @param currentStar the star level before the attempt
     * @param targetStar  the star level targeted on success
     * @param successRate the resolved success rate in percent (0-100)
     */
    public StrengthenPreAttemptEvent(Player player,
            ItemStack targetItem,
            String recipeId,
            int currentStar,
            int targetStar,
            double successRate) {
        this.player = player;
        this.targetItem = targetItem;
        this.recipeId = recipeId;
        this.currentStar = currentStar;
        this.targetStar = targetStar;
        this.successRate = successRate;
    }

    /** {@return the player performing the attempt} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the item being strengthened, or {@code null}} */
    public ItemStack getTargetItem() {
        return targetItem;
    }

    /** {@return the strengthen recipe id, or {@code null}} */
    public String getRecipeId() {
        return recipeId;
    }

    /** {@return the star level before the attempt} */
    public int getCurrentStar() {
        return currentStar;
    }

    /** {@return the star level targeted on success} */
    public int getTargetStar() {
        return targetStar;
    }

    /** {@return the success rate in percent (0-100) used for the roll} */
    public double getSuccessRate() {
        return successRate;
    }

    /**
     * Overrides the success rate used for the roll.
     *
     * @param successRate the new success rate in percent (0-100)
     */
    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
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
