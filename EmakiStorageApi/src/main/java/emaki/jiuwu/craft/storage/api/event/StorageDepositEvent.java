package emaki.jiuwu.craft.storage.api.event;

import java.util.UUID;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before items are moved into a player's storage.
 *
 * <p>This is a plain Bukkit event for third-party consumption, not a CoreLib gameplay fact.
 * It is always fired on the owning entity thread, immediately before the inventory removal and
 * entry-table mutation. Cancelling aborts the whole transaction: nothing is removed from the
 * player's inventory and no entry is changed.
 *
 * <p>{@link #template()} returns a copy with {@code amount == 1}; mutating it has no effect on
 * the stored data.
 */
public class StorageDepositEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final ItemStack template;
    private final long requestedAmount;
    private final String source;
    private boolean cancelled;

    /**
     * Constructs the event. Called by EmakiStorage's transaction service; listeners receive instances
     * rather than creating them.
     *
     * @param playerId        the storage owner
     * @param template        the item being deposited; defensively copied, so later mutation by the
     *                        caller is not visible to listeners
     * @param requestedAmount how many units the caller asked to deposit, which may exceed what the
     *                        storage can actually accept
     * @param source          the originating surface id, one of {@code gui}, {@code command},
     *                        {@code api} or {@code action}
     */
    public StorageDepositEvent(@NotNull UUID playerId,
            @NotNull ItemStack template,
            long requestedAmount,
            @NotNull String source) {
        this.playerId = playerId;
        this.template = template.clone();
        this.requestedAmount = requestedAmount;
        this.source = source;
    }

    /** {@return the storage owner} */
    public @NotNull UUID playerId() {
        return playerId;
    }

    /** {@return a copy of the item being deposited, amount normalised to one} */
    public @NotNull ItemStack template() {
        return template.clone();
    }

    /** {@return how many units the caller asked to deposit} */
    public long requestedAmount() {
        return requestedAmount;
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
