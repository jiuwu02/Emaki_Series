package emaki.jiuwu.craft.storage.api.event;

import java.util.UUID;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before purchased storage slots are added to a player.
 *
 * <p>This is a plain Bukkit event for third-party consumption, not a CoreLib gameplay fact.
 * It is fired after the price has been computed but before any currency or item is taken, so
 * cancelling costs the player nothing.
 */
public class StorageUnlockEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final int slotAmount;
    private final int purchasedSlotsBefore;
    private final double currencyCost;
    private final String source;
    private boolean cancelled;

    /**
     * Constructs the event. Called by EmakiStorage's unlock service after the quote has been validated;
     * listeners receive instances rather than creating them.
     *
     * @param playerId             the storage owner
     * @param slotAmount           how many slots this operation would unlock
     * @param purchasedSlotsBefore how many slots the player had already purchased
     * @param currencyCost         the summed currency price from the accepted quote; zero when the
     *                             purchase does not charge currency
     * @param source               the originating surface id, one of {@code gui}, {@code command},
     *                             {@code api} or {@code action}
     */
    public StorageUnlockEvent(@NotNull UUID playerId,
            int slotAmount,
            int purchasedSlotsBefore,
            double currencyCost,
            @NotNull String source) {
        this.playerId = playerId;
        this.slotAmount = slotAmount;
        this.purchasedSlotsBefore = purchasedSlotsBefore;
        this.currencyCost = currencyCost;
        this.source = source;
    }

    /** {@return the storage owner} */
    public @NotNull UUID playerId() {
        return playerId;
    }

    /** {@return how many slots are being unlocked in this operation} */
    public int slotAmount() {
        return slotAmount;
    }

    /** {@return how many slots the player had already purchased} */
    public int purchasedSlotsBefore() {
        return purchasedSlotsBefore;
    }

    /** {@return the summed currency price, computed per slot rather than unit price times count} */
    public double currencyCost() {
        return currencyCost;
    }

    /** {@return the originating surface: {@code gui}, {@code command}, {@code api} or {@code action}} */
    public @NotNull String source() {
        return source;
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

    /** {@return the shared handler list, as required by the Bukkit event contract} */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
