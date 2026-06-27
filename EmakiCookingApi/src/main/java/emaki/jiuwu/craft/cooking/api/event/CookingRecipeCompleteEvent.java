package emaki.jiuwu.craft.cooking.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiCooking when a recipe completes and is about to deliver its
 * outputs, at the single shared delivery exit shared by every station (chopping
 * board, grinder, juicer, fermentation barrel, oven, steamer and wok).
 *
 * <p>Listeners may inspect the player (may be {@code null} for automatic
 * station completions), the station location, the recipe id/name, the station
 * type and the output count, toggle whether the result is dropped via
 * {@link #setDropResult(boolean)}, or cancel the delivery entirely. A cancelled
 * event stops EmakiCooking from delivering the outputs and running the
 * completion actions. This event is fired on the server thread.
 */
public final class CookingRecipeCompleteEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location location;
    private final String recipeId;
    private final String recipeName;
    private final String stationType;
    private final String phase;
    private final int outputCount;
    private boolean dropResult;
    private boolean cancelled;

    /**
     * Creates a recipe complete event.
     *
     * @param player      the player who triggered completion, may be
     *                    {@code null} for automatic station completions
     * @param location    the station location
     * @param recipeId    the recipe id
     * @param recipeName  the recipe display name
     * @param stationType the station type folder name (chopping_board, grinder,
     *                    juicer, fermentation_barrel, oven, steamer, wok)
     * @param phase       the completion phase tag
     * @param outputCount the number of output entries about to be delivered
     * @param dropResult  whether the result will be dropped instead of given to
     *                    the player's inventory
     */
    public CookingRecipeCompleteEvent(Player player,
            Location location,
            String recipeId,
            String recipeName,
            String stationType,
            String phase,
            int outputCount,
            boolean dropResult) {
        this.player = player;
        this.location = location;
        this.recipeId = recipeId;
        this.recipeName = recipeName;
        this.stationType = stationType;
        this.phase = phase;
        this.outputCount = outputCount;
        this.dropResult = dropResult;
    }

    /** {@return the player who triggered completion, or {@code null}} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the station location} */
    public Location getLocation() {
        return location;
    }

    /** {@return the recipe id} */
    public String getRecipeId() {
        return recipeId;
    }

    /** {@return the recipe display name} */
    public String getRecipeName() {
        return recipeName;
    }

    /** {@return the station type folder name} */
    public String getStationType() {
        return stationType;
    }

    /** {@return the completion phase tag} */
    public String getPhase() {
        return phase;
    }

    /** {@return the number of output entries about to be delivered} */
    public int getOutputCount() {
        return outputCount;
    }

    /** {@return whether the result will be dropped instead of given to inventory} */
    public boolean isDropResult() {
        return dropResult;
    }

    /**
     * Overrides whether the result is dropped on the ground instead of being
     * given to the player's inventory.
     *
     * @param dropResult {@code true} to drop the result
     */
    public void setDropResult(boolean dropResult) {
        this.dropResult = dropResult;
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
