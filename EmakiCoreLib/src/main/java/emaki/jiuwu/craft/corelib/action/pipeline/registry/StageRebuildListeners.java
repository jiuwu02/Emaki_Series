package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class StageRebuildListeners {

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public synchronized boolean register(@Nullable Plugin owner, @Nullable Runnable reregister) {
        if (owner == null || reregister == null) {
            return false;
        }
        String key = keyOf(owner);
        if (key.isEmpty()) {
            return false;
        }
        entries.put(key, new Entry(owner, reregister));
        return true;
    }

    public synchronized boolean remove(@Nullable Plugin owner) {
        return owner != null && entries.remove(keyOf(owner)) != null;
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
    }

    public int notifyRebuilt(@Nullable Consumer<Failure> onFailure) {
        List<Entry> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(entries.values());
        }
        int succeeded = 0;
        List<Plugin> disabled = new ArrayList<>();
        for (Entry entry : snapshot) {
            if (!entry.owner().isEnabled()) {
                disabled.add(entry.owner());
                continue;
            }
            try {
                entry.reregister().run();
                succeeded++;
            } catch (RuntimeException | LinkageError exception) {
                if (onFailure != null) {
                    onFailure.accept(new Failure(keyOf(entry.owner()), exception));
                }
            }
        }
        for (Plugin owner : disabled) {
            remove(owner);
        }
        return succeeded;
    }

    private static String keyOf(Plugin owner) {
        String name = owner.getName();
        return name == null ? "" : name;
    }

    private record Entry(@NotNull Plugin owner, @NotNull Runnable reregister) {
    }

    public record Failure(@NotNull String owner, @NotNull Throwable error) {
    }
}
