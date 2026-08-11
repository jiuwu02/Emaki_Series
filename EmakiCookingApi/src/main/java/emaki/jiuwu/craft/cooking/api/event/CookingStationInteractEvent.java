package emaki.jiuwu.craft.cooking.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired by EmakiCooking when a player interacts with a cooking station block or
 * furniture, at the single shared interaction entry shared by every block
 * source (vanilla, CraftEngine, ItemsAdder, Nexo, Oraxen and their furniture).
 *
 * <p>This event is a read-only notification: it does not implement
 * {@link org.bukkit.event.Cancellable}. Whether the interaction itself is
 * cancelled is still decided by EmakiCooking's own per-station handling. The
 * event fires before EmakiCooking dispatches the interaction to its station
 * services. The built-in tracker records it for
 * {@link emaki.jiuwu.craft.cooking.api.CookingCatalog#recentStation(java.util.UUID)},
 * which is the public query paired with this notification.</p>
 *
 * <p>Listeners may inspect the player, the station location, the station type
 * and the interaction type, for example to drive per-station special recipe
 * conditions. The event is synchronous and fires on the interacting player's
 * entity-owner/location-owner execution boundary.</p>
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
