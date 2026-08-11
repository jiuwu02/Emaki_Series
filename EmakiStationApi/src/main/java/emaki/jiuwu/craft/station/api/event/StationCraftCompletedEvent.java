package emaki.jiuwu.craft.station.api.event;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.station.api.model.PendingOutput;

/**
 * Fired after a craft settled and its outputs were routed.
 *
 * <p><strong>Not cancellable.</strong> By the time it fires the materials are long gone and the
 * outputs have already been handed out or parked for a claim, so there is nothing left to veto.
 *
 * <p><strong>Thread:</strong> the crafting player's owner thread.
 *
 * <p><strong>Coverage:</strong> settlement of a queued entry and immediate settlement of a
 * zero-duration recipe. {@link #getPendingOutputs()} is non-empty when no configured destination could
 * take the outputs and they now await a manual claim, which means "completed" here describes the craft,
 * not necessarily a successful delivery. It does <em>not</em> fire when a player later claims parked
 * outputs, and it is not an audit log: a cancelled entry never reaches this event.
 */
public class StationCraftCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String stationId;
    private final String recipeId;
    private final long batch;
    private final List<PendingOutput> deliveredOutputs;
    private final List<PendingOutput> pendingOutputs;

    /**
     * Creates the event with defensively copied output lists.
     *
     * @param player           the crafting player
     * @param stationId        the station that produced the craft
     * @param recipeId         the recipe that was crafted
     * @param batch            how many times the recipe was applied
     * @param deliveredOutputs outputs that reached the player; {@code null} becomes empty
     * @param pendingOutputs   outputs that could not be delivered; {@code null} becomes empty
     */
    public StationCraftCompletedEvent(@NotNull Player player,
            @NotNull String stationId,
            @NotNull String recipeId,
            long batch,
            List<PendingOutput> deliveredOutputs,
            List<PendingOutput> pendingOutputs) {
        this.player = player;
        this.stationId = stationId;
        this.recipeId = recipeId;
        this.batch = batch;
        this.deliveredOutputs = deliveredOutputs == null ? List.of() : List.copyOf(deliveredOutputs);
        this.pendingOutputs = pendingOutputs == null ? List.of() : List.copyOf(pendingOutputs);
    }

    /** {@return the crafting player} */
    public @NotNull Player getPlayer() {
        return player;
    }

    /** {@return the station that produced the craft} */
    public @NotNull String getStationId() {
        return stationId;
    }

    /** {@return the recipe that was crafted} */
    public @NotNull String getRecipeId() {
        return recipeId;
    }

    /** {@return how many times the recipe was applied} */
    public long getBatch() {
        return batch;
    }

    /** {@return the outputs that actually reached the player; never {@code null}} */
    public @NotNull List<PendingOutput> getDeliveredOutputs() {
        return deliveredOutputs;
    }

    /** {@return the outputs now awaiting a manual claim; empty when everything was delivered} */
    public @NotNull List<PendingOutput> getPendingOutputs() {
        return pendingOutputs;
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
