package emaki.jiuwu.craft.storage.api.event;

import java.util.UUID;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before items are moved out of a player's storage.
 *
 * <p>This is a plain Bukkit event for third-party consumption, not a CoreLib gameplay fact.
 * It is always fired on the owning entity thread, before the entry amount is debited.
 * Cancelling aborts the whole transaction; the stored amount is left untouched.
 */
public class StorageWithdrawEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final ItemStack template;
    private final long requestedAmount;
    private final long storedAmount;
    private final String source;
    private boolean cancelled;

    /**
     * Constructs the event. Called by EmakiStorage's transaction service; listeners receive instances
     * rather than creating them.
     *
     * @param playerId        the storage owner
     * @param template        the item being withdrawn; defensively copied, so later mutation by the
     *                        caller is not visible to listeners
     * @param requestedAmount how many units the caller asked to withdraw, which may exceed
     *                        {@code storedAmount}
     * @param storedAmount    how many units the entry held before this operation
     * @param source          the originating surface id, one of {@code gui}, {@code command},
     *                        {@code api} or {@code action}
     */
    public StorageWithdrawEvent(@NotNull UUID playerId,
            @NotNull ItemStack template,
            long requestedAmount,
            long storedAmount,
            @NotNull String source) {
        this.playerId = playerId;
        this.template = template.clone();
        this.requestedAmount = requestedAmount;
        this.storedAmount = storedAmount;
        this.source = source;
    }

    /** {@return the storage owner} */
    public @NotNull UUID playerId() {
        return playerId;
    }

    /** {@return a copy of the item being withdrawn, amount normalised to one} */
    public @NotNull ItemStack template() {
        return template.clone();
    }

    /** {@return how many units the caller asked to withdraw} */
    public long requestedAmount() {
        return requestedAmount;
    }

    /** {@return how many units the entry held before this operation} */
    public long storedAmount() {
        return storedAmount;
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
