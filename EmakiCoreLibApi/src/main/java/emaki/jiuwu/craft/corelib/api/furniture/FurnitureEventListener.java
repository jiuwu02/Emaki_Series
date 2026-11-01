package emaki.jiuwu.craft.corelib.api.furniture;

import org.jetbrains.annotations.ApiStatus;

/**
 * Sink for normalised furniture events dispatched by CoreLib's furniture relay.
 *
 * <p>Implementations must not block, must not schedule work, and must tolerate any subset of
 * furniture sources being absent. Exceptions thrown by a listener are isolated by the relay and
 * never propagate back into the source plugin's event call.
 */
@ApiStatus.Experimental
@FunctionalInterface
public interface FurnitureEventListener {

    /**
     * Handles one furniture event.
     *
     * <p>Called on the thread of the underlying Bukkit event. Cancelling is done through
     * {@link FurnitureEvent#cancelConsumer()}.
     */
    void onFurnitureEvent(FurnitureEvent event);
}
