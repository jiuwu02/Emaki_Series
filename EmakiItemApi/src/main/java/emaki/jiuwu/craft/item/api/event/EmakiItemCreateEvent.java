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
 * {@code ItemOperations.create} maps it to {@code FailureKind.CANCELLED}. The event is synchronous and is
 * fired only while the global region is owned. The current runtime creation path has no player context, so
 * {@link #getPlayer()} is presently {@code null}.
 */
public final class EmakiItemCreateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String id;
    private final int amount;
    private final Player player;
    private ItemStack result;
    private boolean cancelled;

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

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
