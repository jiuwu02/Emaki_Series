package emaki.jiuwu.craft.corelib.animation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.animation.AnimationDefinition;
import emaki.jiuwu.craft.corelib.api.animation.AnimationListener;

public final class AnimationRegistry {

    private final Map<Plugin, Map<String, AnimationDefinition>> definitions = new ConcurrentHashMap<>();
    private final Map<Plugin, AnimationListener> listeners = new ConcurrentHashMap<>();

    public @Nullable AnimationDefinition register(@Nullable Plugin owner, @Nullable AnimationDefinition definition) {
        if (owner == null || definition == null || !definition.valid()) {
            return null;
        }
        return definitions.computeIfAbsent(owner, key -> new ConcurrentHashMap<>())
                .put(definition.id(), definition);
    }

    public boolean revoke(@Nullable Plugin owner, @Nullable String definitionId) {
        if (owner == null || definitionId == null) {
            return false;
        }
        Map<String, AnimationDefinition> owned = definitions.get(owner);
        return owned != null && owned.remove(definitionId.trim().toLowerCase(Locale.ROOT)) != null;
    }

    public @Nullable AnimationDefinition find(@Nullable Plugin owner, @Nullable String definitionId) {
        if (definitionId == null || definitionId.isBlank()) {
            return null;
        }
        String key = definitionId.trim().toLowerCase(Locale.ROOT);
        if (owner != null) {
            Map<String, AnimationDefinition> owned = definitions.get(owner);
            if (owned != null) {
                AnimationDefinition own = owned.get(key);
                if (own != null) {
                    return own;
                }
            }
        }
        for (Map<String, AnimationDefinition> other : definitions.values()) {
            AnimationDefinition found = other.get(key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public @NotNull Collection<AnimationDefinition> definitions() {
        List<AnimationDefinition> all = new ArrayList<>();
        for (Map<String, AnimationDefinition> owned : definitions.values()) {
            all.addAll(owned.values());
        }
        return all;
    }

    public @Nullable AnimationListener addListener(@Nullable Plugin owner, @Nullable AnimationListener listener) {
        if (owner == null || listener == null) {
            return null;
        }
        return listeners.put(owner, listener);
    }

    public void removeListener(@Nullable Plugin owner) {
        if (owner != null) {
            listeners.remove(owner);
        }
    }

    public @NotNull List<AnimationListener> listeners() {
        return List.copyOf(listeners.values());
    }

    public @NotNull List<AnimationDefinition> revokeAll(@Nullable Plugin owner) {
        if (owner == null) {
            return List.of();
        }
        listeners.remove(owner);
        Map<String, AnimationDefinition> owned = definitions.remove(owner);
        return owned == null ? List.of() : List.copyOf(owned.values());
    }

    public void clear() {
        definitions.clear();
        listeners.clear();
    }
}
