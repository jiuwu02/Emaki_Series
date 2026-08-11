package emaki.jiuwu.craft.station.api.event;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.station.api.model.ConsumedMaterial;

/**
 * Fired before a queued entry is removed and before anything is refunded.
 *
 * <p><strong>Cancellable.</strong> Cancelling keeps the entry in the queue and refunds nothing, so the
 * player's materials stay committed to the craft.
 *
 * <p><strong>Thread:</strong> the owning player's owner thread.
 *
 * <p><strong>Coverage:</strong> player-initiated cancellations from the GUI or a command, and
 * {@link emaki.jiuwu.craft.station.api.StationOperations#cancelAsync}. It does <em>not</em> fire when
 * an entry completes normally, nor when queue data is discarded administratively.
 */
public class StationCraftCancelEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String stationId;
    private final String recipeId;
    private final int index;
    private final List<ConsumedMaterial> consumedMaterials;
    private final double refundRate;
    private boolean cancelled;

    /**
     * Creates the event with a defensively copied material list.
     *
     * @param player            the cancelling player
     * @param stationId         the station the entry belongs to
     * @param recipeId          the recipe being cancelled
     * @param index             the zero-based queue position being cancelled
     * @param consumedMaterials the materials that were debited for this entry; {@code null} becomes empty
     * @param refundRate        the configured refund fraction that will be applied
     */
    public StationCraftCancelEvent(@NotNull Player player,
            @NotNull String stationId,
            @NotNull String recipeId,
            int index,
            List<ConsumedMaterial> consumedMaterials,
            double refundRate) {
        this.player = player;
        this.stationId = stationId;
        this.recipeId = recipeId;
        this.index = index;
        this.consumedMaterials = consumedMaterials == null ? List.of() : List.copyOf(consumedMaterials);
        this.refundRate = refundRate;
    }

    /** {@return the cancelling player} */
    public @NotNull Player getPlayer() {
        return player;
    }

    /** {@return the station the entry belongs to} */
    public @NotNull String getStationId() {
        return stationId;
    }

    /** {@return the recipe being cancelled} */
    public @NotNull String getRecipeId() {
        return recipeId;
    }

    /** {@return the zero-based queue position being cancelled} */
    public int getIndex() {
        return index;
    }

    /** {@return the materials that were debited for this entry; never {@code null}} */
    public @NotNull List<ConsumedMaterial> getConsumedMaterials() {
        return consumedMaterials;
    }

    /** {@return the configured refund fraction that will be applied} */
    public double getRefundRate() {
        return refundRate;
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
