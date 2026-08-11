package emaki.jiuwu.craft.station.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.station.api.model.MaterialChannel;

/**
 * Fired after a submission passed every check but before any material is debited.
 *
 * <p><strong>Cancellable.</strong> Cancelling stops the submission completely: nothing is debited and
 * no queue entry is created. This is the only point at which a listener can veto a craft without
 * having to compensate anything.
 *
 * <p><strong>Thread:</strong> the crafting player's owner thread. On Folia that is the entity
 * scheduler owner; on Paper it is the main server thread.
 *
 * <p><strong>Coverage:</strong> every submission that reaches the debit stage, whether it came from
 * the GUI, a command, or {@link emaki.jiuwu.craft.station.api.StationOperations#submitAsync}. It does
 * <em>not</em> fire for submissions rejected earlier by permission, condition, queue-capacity, or
 * material checks, and it does not fire when a queued entry later advances or settles.
 */
public class StationCraftSubmitEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String stationId;
    private final String recipeId;
    private final long batch;
    private final MaterialChannel channel;
    private boolean cancelled;

    /**
     * Creates the event.
     *
     * @param player    the crafting player
     * @param stationId the station being used
     * @param recipeId  the recipe being crafted
     * @param batch     how many times the recipe is applied
     * @param channel   where the materials will be taken from
     */
    public StationCraftSubmitEvent(@NotNull Player player,
            @NotNull String stationId,
            @NotNull String recipeId,
            long batch,
            @NotNull MaterialChannel channel) {
        this.player = player;
        this.stationId = stationId;
        this.recipeId = recipeId;
        this.batch = batch;
        this.channel = channel;
    }

    /** {@return the crafting player} */
    public @NotNull Player getPlayer() {
        return player;
    }

    /** {@return the station being used} */
    public @NotNull String getStationId() {
        return stationId;
    }

    /** {@return the recipe being crafted} */
    public @NotNull String getRecipeId() {
        return recipeId;
    }

    /** {@return how many times the recipe is applied by this submission} */
    public long getBatch() {
        return batch;
    }

    /** {@return where the materials will be taken from} */
    public @NotNull MaterialChannel getChannel() {
        return channel;
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
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    /** {@return the shared handler list} */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
