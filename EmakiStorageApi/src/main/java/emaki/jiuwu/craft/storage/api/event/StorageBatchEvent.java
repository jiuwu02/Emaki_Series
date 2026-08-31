package emaki.jiuwu.craft.storage.api.event;

import java.util.List;
import java.util.UUID;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.storage.api.model.StorageBatchOp;

/**
 * Fired once after all batch pre-checks pass and before any storage increment is applied.
 *
 * <p>Runs synchronously on the storage owner's entity-owner thread. Cancellation aborts the entire batch,
 * regardless of {@link #allOrNothing()}, and no per-operation deposit/withdraw events are emitted. The event
 * is a read-only transaction snapshot: {@link #ops()} is immutable and each template is defensive-copied
 * with amount {@code 1}.
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
