package emaki.jiuwu.craft.forge.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired by EmakiForge after a forge attempt has fully completed, on the server
 * thread.
 *
 * <p>The attempt has already been resolved and any result item delivered, so
 * this event is informational and cannot be cancelled. It fires for both
 * successful and failed attempts (use {@link #isSuccess()} to distinguish);
 * unexpected execution errors do not fire this event. It is suitable for
 * statistics, announcements and downstream effects.
 */
public final class ForgeCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String recipeId;
    private final boolean success;
    private final ItemStack resultItem;
    private final String quality;
    private final double multiplier;

    /**
     * Creates a forge completed event.
     *
     * @param player     the player who performed the attempt
     * @param recipeId   the forge recipe id
     * @param success    whether the attempt succeeded
     * @param resultItem the produced item, or {@code null} on failure
     * @param quality    the resolved quality tier id, may be blank/{@code null}
     * @param multiplier the resolved quality multiplier
     */
    public ForgeCompletedEvent(Player player,
            String recipeId,
            boolean success,
            ItemStack resultItem,
            String quality,
            double multiplier) {
        this.player = player;
        this.recipeId = recipeId;
        this.success = success;
        this.resultItem = resultItem;
        this.quality = quality;
        this.multiplier = multiplier;
    }

    /** {@return the player who performed the attempt} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the forge recipe id} */
    public String getRecipeId() {
        return recipeId;
    }

    /** {@return whether the attempt succeeded} */
    public boolean isSuccess() {
        return success;
    }

    /** {@return the produced item, or {@code null} on failure} */
    public ItemStack getResultItem() {
        return resultItem;
    }

    /** {@return the resolved quality tier id, may be blank or {@code null}} */
    public String getQuality() {
        return quality;
    }

    /** {@return the resolved quality multiplier} */
    public double getMultiplier() {
        return multiplier;
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
