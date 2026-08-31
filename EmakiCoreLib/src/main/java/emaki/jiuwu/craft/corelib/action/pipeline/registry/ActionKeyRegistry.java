package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;

public final class ActionKeyRegistry {

    private final Object writeLock = new Object();

    private volatile Map<String, Declaration> declarations = Map.of();

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

    public @Nullable Declaration find(@Nullable String name) {
        return name == null ? null : declarations.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public @NotNull Map<String, Declaration> all() {
        return declarations;
    }

    public void clear() {
        synchronized (writeLock) {
            declarations = Map.of();
        }
    }

    public record Declaration(@NotNull String name, @NotNull Class<?> type, @NotNull String declaredBy) {
    }
}
