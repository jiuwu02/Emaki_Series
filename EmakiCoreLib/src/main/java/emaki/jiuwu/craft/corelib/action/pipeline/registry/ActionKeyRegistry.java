package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;

/**
 * Detects context keys declared with the same name but different types.
 *
 * <p>{@link CoreActionKey} equality is name-only, so two modules declaring {@code "item"} with
 * different types would silently share one slot. This registry turns that into a startup error.</p>
 */
public final class ActionKeyRegistry {

    private final Object writeLock = new Object();

    private volatile Map<String, Declaration> declarations = Map.of();

    /**
     * Records one key declaration.
     *
     * @param key the declared key
     * @param declaredBy who declared it, used in the conflict message
     * @return {@code null} when accepted, or the conflicting declaration
     */
    public @Nullable Declaration declare(@Nullable CoreActionKey<?> key, @Nullable String declaredBy) {
        if (key == null) {
            return null;
        }
        synchronized (writeLock) {
            Declaration existing = declarations.get(key.name());
            if (existing != null) {
                return existing.type() == key.type() ? null : existing;
            }
            Map<String, Declaration> copy = new LinkedHashMap<>(declarations);
            copy.put(key.name(), new Declaration(key.name(), key.type(),
                    declaredBy == null ? "" : declaredBy));
            declarations = Map.copyOf(copy);
            return null;
        }
    }

    /**
     * Reads a recorded declaration.
     *
     * @param name key name
     * @return the declaration, or {@code null} when the name is unknown
     */
    public @Nullable Declaration find(@Nullable String name) {
        return name == null ? null : declarations.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /** {@return every recorded declaration, keyed by key name} */
    public @NotNull Map<String, Declaration> all() {
        return declarations;
    }

    /** Clears every declaration. */
    public void clear() {
        synchronized (writeLock) {
            declarations = Map.of();
        }
    }

    /**
     * One recorded key declaration.
     *
     * @param name key name
     * @param type declared value type
     * @param declaredBy who declared it
     */
    public record Declaration(@NotNull String name, @NotNull Class<?> type, @NotNull String declaredBy) {
    }
}
