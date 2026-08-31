package emaki.jiuwu.craft.corelib.readiness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.readiness.ModuleReadinessListener;
import emaki.jiuwu.craft.corelib.api.readiness.ModuleReadinessPhase;
import emaki.jiuwu.craft.corelib.api.readiness.ReadinessRegistration;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ModuleReadinessRegistry {

    private final Map<String, Boolean> states = new LinkedHashMap<>();
    private final Map<String, List<Entry>> waiters = new LinkedHashMap<>();

    private final Map<String, Map<String, ListenerEntry>> listeners = new LinkedHashMap<>();

    public @NotNull ReadinessRegistration whenReady(@Nullable Plugin owner,
            @Nullable String moduleName,
            @Nullable Runnable callback,
            @Nullable Consumer<Failure> onFailure) {
        if (owner == null || callback == null || Texts.isBlank(moduleName)) {
            return ReadinessRegistration.inactive();
        }
        String key = keyOf(moduleName);
        Entry entry = new Entry(owner, callback);
        synchronized (this) {
            if (!Boolean.TRUE.equals(states.get(key))) {
                waiters.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
                return new Handle(this, key, entry);
            }
        }

        run(entry, key, onFailure);
        return ReadinessRegistration.inactive();
    }

    public @NotNull ReadinessRegistration addListener(@Nullable Plugin owner,
            @Nullable String moduleName,
            @Nullable ModuleReadinessListener listener) {
        if (owner == null || listener == null || Texts.isBlank(moduleName)) {
            return ReadinessRegistration.inactive();
        }
        String moduleKey = keyOf(moduleName);
        String ownerKey = keyOf(owner.getName());
        if (ownerKey.isEmpty()) {
            return ReadinessRegistration.inactive();
        }
        ListenerEntry entry = new ListenerEntry(owner, listener);
        synchronized (this) {
            listeners.computeIfAbsent(moduleKey, ignored -> new LinkedHashMap<>()).put(ownerKey, entry);
        }
        return new ListenerHandle(this, moduleKey, ownerKey, entry);
    }

    public int markReady(@Nullable String moduleName, @Nullable Consumer<Failure> onFailure) {
        if (Texts.isBlank(moduleName)) {
            return 0;
        }
        String key = keyOf(moduleName);
        List<Entry> snapshot;
        boolean becameReady;
        synchronized (this) {

            becameReady = !Boolean.TRUE.equals(states.get(key));
            states.put(key, Boolean.TRUE);

            List<Entry> pending = waiters.remove(key);
            snapshot = pending == null ? List.of() : List.copyOf(pending);
        }

        if (becameReady) {
            notifyListeners(key, ModuleReadinessPhase.READY, onFailure);
        }
        int succeeded = 0;
        for (Entry entry : snapshot) {

            if (entry.owner().isEnabled() && run(entry, key, onFailure)) {
                succeeded++;
            }
        }
        return succeeded;
    }

    public void markLoading(@Nullable String moduleName, @Nullable Consumer<Failure> onFailure) {
        if (Texts.isBlank(moduleName)) {
            return;
        }
        String key = keyOf(moduleName);
        boolean becameLoading;
        synchronized (this) {

            becameLoading = !Boolean.FALSE.equals(states.get(key));
            states.put(key, Boolean.FALSE);
        }
        if (becameLoading) {
            notifyListeners(key, ModuleReadinessPhase.LOADING, onFailure);
        }
    }

    public void markAbsent(@Nullable String moduleName, @Nullable Consumer<Failure> onFailure) {
        if (Texts.isBlank(moduleName)) {
            return;
        }
        String key = keyOf(moduleName);
        boolean hadState;
        synchronized (this) {
            hadState = states.remove(key) != null;
        }
        if (hadState) {
            notifyListeners(key, ModuleReadinessPhase.ABSENT, onFailure);
        }
    }

    public synchronized boolean isReady(@Nullable String moduleName) {
        return !Texts.isBlank(moduleName) && Boolean.TRUE.equals(states.get(keyOf(moduleName)));
    }

    public synchronized int removeOwner(@Nullable Plugin owner) {
        if (owner == null) {
            return 0;
        }
        int removed = 0;
        for (Map.Entry<String, List<Entry>> pending : List.copyOf(waiters.entrySet())) {
            List<Entry> entries = pending.getValue();
            int before = entries.size();
            entries.removeIf(entry -> entry.owner() == owner);
            removed += before - entries.size();
            if (entries.isEmpty()) {
                waiters.remove(pending.getKey());
            }
        }
        for (Map.Entry<String, Map<String, ListenerEntry>> perModule : List.copyOf(listeners.entrySet())) {
            Map<String, ListenerEntry> owners = perModule.getValue();
            int before = owners.size();
            owners.values().removeIf(entry -> entry.owner() == owner);
            removed += before - owners.size();
            if (owners.isEmpty()) {
                listeners.remove(perModule.getKey());
            }
        }
        return removed;
    }

    public synchronized int pendingCount() {
        int total = 0;
        for (List<Entry> entries : waiters.values()) {
            total += entries.size();
        }
        return total;
    }

    public synchronized void clear() {
        states.clear();
        waiters.clear();
        listeners.clear();
    }

    public synchronized int listenerCount() {
        int total = 0;
        for (Map<String, ListenerEntry> owners : listeners.values()) {
            total += owners.size();
        }
        return total;
    }

    private void notifyListeners(String moduleKey,
            ModuleReadinessPhase phase,
            Consumer<Failure> onFailure) {
        List<ListenerEntry> snapshot;
        synchronized (this) {
            Map<String, ListenerEntry> owners = listeners.get(moduleKey);
            if (owners == null || owners.isEmpty()) {
                return;
            }
            snapshot = List.copyOf(owners.values());
        }
        List<ListenerEntry> disabled = new ArrayList<>();
        for (ListenerEntry entry : snapshot) {
            if (!entry.owner().isEnabled()) {
                disabled.add(entry);
                continue;
            }
            try {
                entry.listener().onReadinessChanged(phase);
            } catch (RuntimeException | LinkageError exception) {
                if (onFailure != null) {
                    onFailure.accept(new Failure(entry.owner().getName(), moduleKey, exception));
                }
            }
        }
        for (ListenerEntry entry : disabled) {
            removeListener(moduleKey, keyOf(entry.owner().getName()), entry);
        }
    }

    private boolean run(Entry entry, String moduleName, Consumer<Failure> onFailure) {
        try {
            entry.callback().run();
            return true;
        } catch (RuntimeException | LinkageError exception) {
            if (onFailure != null) {
                onFailure.accept(new Failure(entry.owner().getName(), moduleName, exception));
            }
            return false;
        }
    }

    private synchronized void remove(String moduleName, Entry entry) {
        List<Entry> pending = waiters.get(moduleName);
        if (pending == null) {
            return;
        }
        pending.removeIf(candidate -> candidate == entry);
        if (pending.isEmpty()) {
            waiters.remove(moduleName);
        }
    }

    private synchronized void removeListener(String moduleKey, String ownerKey, ListenerEntry expected) {
        Map<String, ListenerEntry> owners = listeners.get(moduleKey);
        if (owners == null) {
            return;
        }
        owners.remove(ownerKey, expected);
        if (owners.isEmpty()) {
            listeners.remove(moduleKey);
        }
    }

    private static String keyOf(String moduleName) {
        return Texts.lower(moduleName).trim();
    }

    private record Entry(@NotNull Plugin owner, @NotNull Runnable callback) {
    }

    private record ListenerEntry(@NotNull Plugin owner, @NotNull ModuleReadinessListener listener) {
    }

    public record Failure(@Nullable String owner, @NotNull String moduleName, @NotNull Throwable error) {
    }

    private static final class Handle implements ReadinessRegistration {

        private final ModuleReadinessRegistry registry;
        private final String moduleName;
        private final Entry entry;

        private volatile boolean active = true;

        private Handle(ModuleReadinessRegistry registry, String moduleName, Entry entry) {
            this.registry = registry;
            this.moduleName = moduleName;
            this.entry = entry;
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
            registry.remove(moduleName, entry);
        }
    }

    private static final class ListenerHandle implements ReadinessRegistration {

        private final ModuleReadinessRegistry registry;
        private final String moduleKey;
        private final String ownerKey;
        private final ListenerEntry entry;

        private volatile boolean active = true;

        private ListenerHandle(ModuleReadinessRegistry registry,
                String moduleKey,
                String ownerKey,
                ListenerEntry entry) {
            this.registry = registry;
            this.moduleKey = moduleKey;
            this.ownerKey = ownerKey;
            this.entry = entry;
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
            registry.removeListener(moduleKey, ownerKey, entry);
        }
    }
}
