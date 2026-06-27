package emaki.jiuwu.craft.gem.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Fired by EmakiGem after an upgrade request has passed validation but before
 * any cost is charged and the success roll decides the outcome.
 *
 * <p>Listeners may inspect the actor, the gem item (or the equipment for an
 * equipped-gem upgrade), the current/target level, override the success chance
 * via {@link #setSuccessChance(double)}, or cancel the upgrade entirely. A
 * cancelled event stops EmakiGem from charging and rolling. This event is fired
 * on the server thread.
 */
public final class GemUpgradeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack gemItem;
    private final String gemId;
    private final int currentLevel;
    private final int targetLevel;
    private final int slotIndex;
    private double successChance;
    private boolean cancelled;

    /**
     * Creates an upgrade event.
     *
     * @param player        the player performing the upgrade
     * @param gemItem       the gem item being upgraded, or the equipment for an
     *                      equipped-gem upgrade
     * @param gemId         the gem definition id
     * @param currentLevel  the gem level before the upgrade
     * @param targetLevel   the gem level after a successful upgrade
     * @param slotIndex     the socket slot index for an equipped-gem upgrade, or
     *                      {@code -1} when upgrading a gem item directly
     * @param successChance the resolved success chance in percent (0-100)
     */
    public GemUpgradeEvent(Player player,
            ItemStack gemItem,
            String gemId,
            int currentLevel,
            int targetLevel,
            int slotIndex,
            double successChance) {
        this.player = player;
        this.gemItem = gemItem;
        this.gemId = gemId;
        this.currentLevel = currentLevel;
        this.targetLevel = targetLevel;
        this.slotIndex = slotIndex;
        this.successChance = successChance;
    }

    /** {@return the player performing the upgrade} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the gem item being upgraded, or the equipment for an equipped upgrade} */
    public ItemStack getGemItem() {
        return gemItem;
    }

    /** {@return the gem definition id} */
    public String getGemId() {
        return gemId;
    }

    /** {@return the gem level before the upgrade} */
    public int getCurrentLevel() {
        return currentLevel;
    }

    /** {@return the gem level after a successful upgrade} */
    public int getTargetLevel() {
        return targetLevel;
    }

    /** {@return the socket slot index for an equipped upgrade, or {@code -1}} */
    public int getSlotIndex() {
        return slotIndex;
    }

    /** {@return the success chance in percent (0-100) used for the roll} */
    public double getSuccessChance() {
        return successChance;
    }

    /**
     * Overrides the success chance used for the roll.
     *
     * @param successChance the new success chance in percent (0-100)
     */
    public void setSuccessChance(double successChance) {
        this.successChance = successChance;
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
