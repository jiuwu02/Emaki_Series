package emaki.jiuwu.craft.corelib.api.action.v2;

import java.util.Locale;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Typed context key, replacing the v1 untyped {@code attributes} / {@code sharedState} maps.
 *
 * <p>Equality is based on {@link #name()} alone, so two keys that share a name but declare
 * different types collide. CoreLib rejects such a collision at registration time instead of letting
 * a stage read a wrongly typed value at runtime.</p>
 *
 * @param <T> value type stored under this key
 */
public final class CoreActionKey<T> {

    private final String name;
    private final Class<T> type;

    private CoreActionKey(String name, Class<T> type) {
        this.name = name;
        this.type = type;
    }

    /**
     * Creates a key.
     *
     * @param name lowercase key name, normalised with {@link Locale#ROOT}
     * @param type declared value type
     * @param <T> value type
     * @return the key
     */
    public static <T> @NotNull CoreActionKey<T> of(@NotNull String name, @NotNull Class<T> type) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("key name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("key type must not be null");
        }
        return new CoreActionKey<>(name.trim().toLowerCase(Locale.ROOT), type);
    }

    /** {@return the normalised key name} */
    public @NotNull String name() {
        return name;
    }

    /** {@return the declared value type} */
    public @NotNull Class<T> type() {
        return type;
    }

    /**
     * Casts {@code value} to this key's type.
     *
     * @param value raw value
     * @return the typed value, or {@code null} when {@code value} is {@code null} or mistyped
     */
    public @Nullable T cast(@Nullable Object value) {
        return type.isInstance(value) ? type.cast(value) : null;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CoreActionKey<?> key && name.equals(key.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }

    @Override
    public String toString() {
        return name + ":" + type.getSimpleName();
    }
}
