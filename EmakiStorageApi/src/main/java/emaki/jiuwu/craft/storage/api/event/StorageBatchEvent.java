package emaki.jiuwu.craft.storage.api.event;

import java.util.List;
import java.util.UUID;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.storage.api.model.StorageBatchOp;

/**
 * Fired once before an atomic batch of storage increments is applied.
 *
 * <p><strong>Timing:</strong> after every op has passed its pre-check and before any debit or credit
 * happens, so a listener sees a batch that is known to be applicable.
 *
 * <p><strong>Thread:</strong> the storage owner's entity owner thread, synchronously.
 *
 * <p><strong>Cancellation:</strong> cancelling aborts the entire batch. No op is applied and stored
 * amounts are left untouched, regardless of {@link #allOrNothing()}.
 *
 * <p><strong>Not fired:</strong> a batch does <em>not</em> fire {@link StorageDepositEvent} or
 * {@link StorageWithdrawEvent} for its individual ops. This is deliberate rather than an oversight:
 * the plugins that submit batches are usually the same plugins that listen for deposits and
 * withdrawals, and per-op fan-out would call their own listeners recursively in the middle of their
 * own transaction. Listen for this event when you care about batch traffic.
 *
 * <p>The event carries no writable business field. It is a transaction snapshot: the op list is
 * immutable and every template is a copy with {@code amount == 1}, so a listener cannot rewrite the
 * batch it is being asked to approve.
 */
public class StorageBatchEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final List<StorageBatchOp> ops;
    private final boolean allOrNothing;
    private final String source;
    private boolean cancelled;

    /**
     * Creates the event for a pre-checked batch.
     *
     * @param playerId the storage owner
     * @param ops the pre-checked increments, in application order
     * @param allOrNothing whether the batch aborts entirely on a single failure
     * @param source the originating surface
     */
    public StorageBatchEvent(@NotNull UUID playerId,
            @NotNull List<StorageBatchOp> ops,
            boolean allOrNothing,
            @NotNull String source) {
        this.playerId = playerId;
        this.ops = List.copyOf(ops);
        this.allOrNothing = allOrNothing;
        this.source = source;
    }

    /** {@return the storage owner} */
    public @NotNull UUID playerId() {
        return playerId;
    }

    /** {@return the immutable increments, each template a copy with {@code amount == 1}} */
    public @NotNull List<StorageBatchOp> ops() {
        return ops;
    }

    /** {@return whether a single failure aborts the whole batch} */
    public boolean allOrNothing() {
        return allOrNothing;
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

    /** {@return the shared handler list} */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
