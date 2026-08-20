package emaki.jiuwu.craft.item.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.item.api.ItemStateKey;

/**
 * Fired after a committed numeric item-state mutation crossed a configured threshold.
 *
 * <p><strong>Precondition and completion.</strong> The owning mutation is already committed to the
 * backing {@link org.bukkit.persistence.PersistentDataContainer} of the affected stack, and the
 * re-arm bookkeeping for {@link #isOnce() once} thresholds has already been persisted, before this
 * event is dispatched. The event therefore reports a completed crossing, never a pending one.
 *
 * <p><strong>Cancellable.</strong> No. The state change cannot be undone through this event; a
 * listener that wants to revert the value must issue its own mutation, and must expect that
 * mutation to be observed again by this event and by {@link ItemStateChangeEvent}.
 *
 * <p><strong>Thread.</strong> Dispatched synchronously on the thread that performed the mutation,
 * which is the owner thread of the mutating context (global or entity owner thread under Folia).
 * Listeners must not assume the global region thread and must not touch a player, inventory or
 * world they do not own.
 *
 * <p><strong>Coverage.</strong> Only numeric state keys ({@code INTEGER}, {@code LONG},
 * {@code DOUBLE}) that have at least one threshold configured under the {@code item_state} config
 * section are evaluated. One event is dispatched per crossed threshold, in ascending threshold
 * order for upward crossings and descending order for downward crossings.
 *
 * <p><strong>Not fired.</strong> For rejected or unchanged mutations; for reserved
 * {@code meta.} metadata keys; for {@code BOOLEAN} and {@code STRING} keys; for numeric keys
 * without configured thresholds; when the old and the new value sit on the same side of every
 * configured threshold; for a {@link #isOnce() once} threshold that is still latched from an
 * earlier crossing and has not been re-armed by falling back below it; and for state that is
 * restored verbatim by rebuild preservation, which is a no-op replay rather than a mutation.
 *
 * <p>This event is not an audit log: it reports crossings observed by this runtime only, and
 * carries no history of crossings that happened before the stack was loaded.
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
