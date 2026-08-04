package emaki.jiuwu.craft.station.gui;

import java.util.Map;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.station.queue.CraftQueue;
import emaki.jiuwu.craft.station.recipe.MaterialRequirement;

/**
 * What the renderer needs to know about a session's materials and queue.
 *
 * <p>Kept as an interface so the renderer never reaches into the channels, the queue cache, or any warehouse
 * future directly. Warehouse counts in particular are asynchronous, and the renderer runs on the owner thread
 * during a synchronous draw; the implementation is responsible for serving an already-cached snapshot rather
 * than blocking a render on a round trip.
 */
public interface StationMaterialView {

    /**
     * Reads how many units of a requirement the viewer currently has in the active channel.
     *
     * @param session     the station session
     * @param requirement the requirement being displayed
     * @return the available units; zero when nothing is known yet
     */
    long ownedOf(StationGuiSession session, MaterialRequirement requirement);

    /**
     * Reads the available counts per identity for the active channel.
     *
     * @param session the station session
     * @return the counts; never {@code null}
     */
    Map<ItemSourceRef, Long> availableOf(StationGuiSession session);

    /**
     * Reads the viewer's queue at the session's station.
     *
     * @param session the station session
     * @return the queue, or {@code null} when the viewer has none
     */
    CraftQueue queueOf(StationGuiSession session);

    /** {@return whether the warehouse is reachable right now} */
    boolean storageUsable();
}
