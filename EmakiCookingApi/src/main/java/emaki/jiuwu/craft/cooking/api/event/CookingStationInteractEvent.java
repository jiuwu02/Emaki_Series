package emaki.jiuwu.craft.cooking.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired before EmakiCooking dispatches an interaction to a station service.
 *
 * <p>Runs synchronously on the interacting player's and location's owner boundary and covers vanilla,
 * supported custom-block and furniture sources. It is informational and cannot cancel the interaction;
 * EmakiCooking retains that decision. The same notification updates
 * {@link emaki.jiuwu.craft.cooking.api.CookingCatalog#recentStation(java.util.UUID)}. Player and location
 * accessors expose live Bukkit objects.
 */
public final class CookingStationInteractEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location location;
    private final String stationType;
    private final String interactionType;

    /**
     * Creates a station interact event.
     *
     * @param player          the interacting player, may be {@code null}
     * @param location        the station block location
     * @param stationType     the station type folder name (chopping_board,
     *                        grinder, juicer, fermentation_barrel, oven,
     *                        steamer, wok)
     * @param interactionType the interaction type tag (left_click, right_click,
     *                        shift_left_click, shift_right_click), may be empty
     */
    public CookingStationInteractEvent(Player player,
            Location location,
            String stationType,
            String interactionType) {
        this.player = player;
        this.location = location;
        this.stationType = stationType == null ? "" : stationType;
        this.interactionType = interactionType == null ? "" : interactionType;
    }

    /** {@return the interacting player, or {@code null}} */
    public Player getPlayer() {
        return player;
    }

    /** {@return the station block location} */
    public Location getLocation() {
        return location;
    }

    /** {@return the station type folder name} */
    public String getStationType() {
        return stationType;
    }

    /** {@return the interaction type tag, or an empty string when unknown} */
    public String getInteractionType() {
        return interactionType;
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
