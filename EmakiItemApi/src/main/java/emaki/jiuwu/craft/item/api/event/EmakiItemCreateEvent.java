package emaki.jiuwu.craft.item.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired after EmakiItem builds a stack but before the caller can commit or receive it.
 *
 * <p>Listeners may replace the result or cancel creation. Cancellation discards the built stack; public
 * {@code ItemOperations.create} maps it to {@code FailureKind.CANCELLED}. The current runtime creation path
 * has no player context, so {@link #getPlayer()} is presently {@code null}.
 *
 * <h2>Threading</h2>
 * Fired synchronously on the calling thread, and only when that thread owns the global region. Listeners
 * therefore run before the stack reaches the caller and may mutate it in place.
 *
 * <h2>Coverage &mdash; creation can happen without this event</h2>
 * When the creating thread does not own the global region, EmakiItem skips the event entirely and returns
 * the built stack unchanged. A missing event means "not observed", not "nothing was created", so do not
 * treat this as an exhaustive record of item creation or as a reliable veto point.
 */
public final class EmakiItemCreateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String id;
    private final int amount;
    private final Player player;
    private ItemStack result;
    private boolean cancelled;

    /**
     * Creates an item creation event. Called by EmakiItem's creation service; third-party plugins listen
     * for this event rather than constructing it.
     *
     * @param id     the resolved definition id; {@code null} is stored as an empty string
     * @param amount the built stack amount; values below one are raised to one
     * @param player the creation player, or {@code null} when the path has no player context
     * @param result the built stack, mutated in place by listeners or replaced through
     *               {@link #setResult(ItemStack)}
     * @throws NullPointerException when {@code result} is {@code null}
     */
    public EmakiItemCreateEvent(@NotNull String id,
                                int amount,
                                @Nullable Player player,
                                @NotNull ItemStack result) {
        if (result == null) {
            throw new NullPointerException("result");
        }
        this.id = id == null ? "" : id;
        this.amount = Math.max(1, amount);
        this.player = player;
        this.result = result;
    }

    /** {@return the resolved definition id} */
    public @NotNull String getId() {
        return id;
    }

    /** {@return the amount already clamped by the built stack's maximum size} */
    public int getAmount() {
        return amount;
    }

    /** {@return the creation player when one is supplied, otherwise {@code null}} */
    public @Nullable Player getPlayer() {
        return player;
    }

    /** {@return the stack that will be returned when the event is not cancelled} */
    public @NotNull ItemStack getResult() {
        return result;
    }

    /**
     * Replaces the stack returned by the creation service.
     *
     * @param result non-null replacement stack
     */
    public void setResult(@NotNull ItemStack result) {
        if (result == null) {
            throw new NullPointerException("result");
        }
        this.result = result;
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

    /** {@return the shared handler list for this event type} */
    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
