package emaki.jiuwu.craft.item.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Result of one item-state mutation attempt. */
public record ItemStateMutation<T>(
        @NotNull ItemStateKey<T> key,
        @Nullable T oldValue,
        @Nullable T newValue,
        @Nullable Number delta,
        boolean committed,
        boolean changed,
        boolean clamped,
        boolean rejected,
        @NotNull String reason) {

    public ItemStateMutation {
        reason = reason == null ? "" : reason;
    }

    public static <T> ItemStateMutation<T> rejected(ItemStateKey<T> key, String reason, T oldValue) {
        return new ItemStateMutation<>(key, oldValue, oldValue, null, false, false, false, true, reason);
    }

    public static <T> ItemStateMutation<T> committed(ItemStateKey<T> key, T oldValue, T newValue,
            Number delta, boolean clamped) {
        return new ItemStateMutation<>(key, oldValue, newValue, delta, true,
                oldValue == null ? newValue != null : !oldValue.equals(newValue), clamped, false, "");
    }
}
