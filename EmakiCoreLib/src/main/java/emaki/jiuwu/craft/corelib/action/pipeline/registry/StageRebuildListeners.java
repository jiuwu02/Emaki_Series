package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageRebuildRegistration;

public final class StageRebuildListeners {

    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Map<Long, Entry> independent = new LinkedHashMap<>();

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

    public synchronized @NotNull CoreStageRebuildRegistration add(@Nullable Plugin owner,
            @Nullable Runnable reregister) {
        if (owner == null || reregister == null || keyOf(owner).isEmpty()) {
            return CoreStageRebuildRegistration.inactive();
        }
        long registration = sequence.incrementAndGet();
        independent.put(registration, new Entry(owner, reregister));
        return new Handle(this, registration);
    }

    public synchronized boolean remove(@Nullable Plugin owner) {
        if (owner == null) {
            return false;
        }
        boolean removed = entries.remove(keyOf(owner)) != null;
        for (Map.Entry<Long, Entry> entry : List.copyOf(independent.entrySet())) {
            if (entry.getValue().owner() == owner) {
                independent.remove(entry.getKey());
                removed = true;
            }
        }
        return removed;
    }

    public synchronized int size() {
        return entries.size() + independent.size();
    }

    public synchronized void clear() {
        entries.clear();
        independent.clear();
    }

    public int notifyRebuilt(@Nullable Consumer<Failure> onFailure) {
        List<Entry> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(entries.values());
            snapshot.addAll(independent.values());
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

    private synchronized boolean remove(long registration) {
        return independent.remove(registration) != null;
    }

    private static String keyOf(Plugin owner) {
        String name = owner.getName();
        return name == null ? "" : name;
    }

    private static final class Handle implements CoreStageRebuildRegistration {

        private final StageRebuildListeners listeners;
        private final long registration;
        private volatile boolean active = true;

        private Handle(StageRebuildListeners listeners, long registration) {
            this.listeners = listeners;
            this.registration = registration;
        }

        @Override
        public boolean active() {
            return active;
        }

        @Override
        public void close() {
            if (!active) {
                return;
            }
            active = false;
            listeners.remove(registration);
        }
    }

    private record Entry(@NotNull Plugin owner, @NotNull Runnable reregister) {
    }

    public record Failure(@NotNull String owner, @NotNull Throwable error) {
    }
}
