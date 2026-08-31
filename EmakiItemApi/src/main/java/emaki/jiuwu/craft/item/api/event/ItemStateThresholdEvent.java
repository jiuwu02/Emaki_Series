package emaki.jiuwu.craft.item.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.item.api.ItemStateKey;

/**
 * Fired after a numeric item-state mutation and its once-threshold bookkeeping are committed.
 *
 * <p>Runs synchronously on the mutating context's owner thread and is informational; reverting requires a
 * separate mutation, which may emit events again. One event is emitted per configured threshold crossed,
 * ascending for upward crossings and descending for downward crossings. Rejected/unchanged writes,
 * metadata or non-numeric keys, missing thresholds, same-side changes, still-latched once thresholds and
 * rebuild preservation do not emit it.
 *
 * <p>The item accessor returns a defensive copy. The holder is a live player reference and may be
 * {@code null} when the mutating call site did not know an owner.
 */
public final class ItemStateThresholdEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    /** Direction in which a threshold was crossed. */
    public enum Direction {
        /** The value rose from below the threshold to at or above it. */
        UP,
        /** The value fell from at or above the threshold to below it. */
        DOWN
    }

    private final ItemStack item;
    private final Player holder;
    private final ItemStateKey<?> key;
    private final Number oldValue;
    private final Number newValue;
    private final Number threshold;
    private final String thresholdId;
    private final Direction direction;
    private final boolean once;
    private final boolean rearmed;

    public ItemStateThresholdEvent(@Nullable ItemStack item,
            @Nullable Player holder,
            @NotNull ItemStateKey<?> key,
            @Nullable Number oldValue,
            @Nullable Number newValue,
            @NotNull Number threshold,
            @NotNull String thresholdId,
            @NotNull Direction direction,
            boolean once,
            boolean rearmed) {
        this.item = item == null ? null : item.clone();
        this.holder = holder;
        this.key = key;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.threshold = threshold;
        this.thresholdId = thresholdId == null ? "" : thresholdId;
        this.direction = direction;
        this.once = once;
        this.rearmed = rearmed;
    }

    /** {@return a defensive copy of the stack that carries the state, or {@code null}} */
    public @Nullable ItemStack getItem() {
        return item == null ? null : item.clone();
    }

    /**
     * {@return the player that held the stack when the mutation ran, or {@code null}}
     *
     * <p>{@code null} means the mutation had no known holder, not that the stack is unheld.
     */
    public @Nullable Player getHolder() {
        return holder;
    }

    /** {@return the mutated state key} */
    public @NotNull ItemStateKey<?> getKey() {
        return key;
    }

    /** {@return the value before the mutation, or {@code null} when the field was absent} */
    public @Nullable Number getOldValue() {
        return oldValue;
    }

    /** {@return the committed value after the mutation} */
    public @Nullable Number getNewValue() {
        return newValue;
    }

    /** {@return the configured threshold that was crossed} */
    public @NotNull Number getThreshold() {
        return threshold;
    }

    /** {@return the stable configured id of the crossed threshold} */
    public @NotNull String getThresholdId() {
        return thresholdId;
    }

    /** {@return the direction of the crossing} */
    public @NotNull Direction getDirection() {
        return direction;
    }

    /** {@return whether the threshold rewards only once until it is re-armed} */
    public boolean isOnce() {
        return once;
    }

    /**
     * {@return whether this dispatch re-armed a latched {@link #isOnce() once} threshold}
     *
     * <p>Only ever {@code true} together with {@link Direction#DOWN}.
     */
    public boolean isRearmed() {
        return rearmed;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
