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

/**
 * The one place a module's "my data is loaded" signal is published and waited on, shaped after
 * {@link emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRebuildListeners}: owner-scoped
 * entries, callbacks run outside the lock, a disabled owner dropped rather than called, and one
 * broken callback never costing the others their notification.
 *
 * <p>It exists because plugin dependencies cannot express this. {@code softdepend} only orders
 * {@code onEnable} calls, while a module that loads asynchronously and publishes through the
 * scheduler finishes after the enable loop has moved on. A consumer reading {@code status().usable()}
 * from its own {@code onEnable} therefore sees {@code false} structurally, not intermittently.</p>
 *
 * <p>Like {@link emaki.jiuwu.craft.corelib.capability.CapabilityRegistry} this table is
 * <strong>not</strong> rebuilt by a CoreLib reload: a waiting relationship belongs to the plugin that
 * asked to wait, so re-reading CoreLib's own config must not silently cancel it. The table is cleared
 * only when CoreLib itself shuts down.</p>
 *
 * <p>A waiter fires once and is then dropped. Waiters that have <em>not</em> fired are kept across a
 * {@code markLoading}, so a consumer that registered mid-reload still gets its one call. Firing once
 * is what makes a repeated {@code markReady} harmless, which matters because a module publishes from
 * more than one transition point.</p>
 *
 * <p>Standing listeners are a second, independent table. They answer "has it reloaded since", which a
 * one-shot waiter structurally cannot: a consumer caching another module's content must invalidate on
 * {@code LOADING} and rebuild on {@code READY}, every time. Because they are not consumed by firing,
 * a repeated {@code markReady} is <em>not</em> harmless for them, so transitions are edge-detected
 * here rather than in each publishing module &mdash; only one of the thirteen modules currently does
 * that check itself.</p>
 */
public final class ModuleReadinessRegistry {

    private final Map<String, Boolean> states = new LinkedHashMap<>();
    private final Map<String, List<Entry>> waiters = new LinkedHashMap<>();
    // module key -> owner key -> listener. Keyed by owner so a second registration replaces rather
    // than accumulates: a plugin whose onEnable runs twice must not rebuild its cache twice.
    private final Map<String, Map<String, ListenerEntry>> listeners = new LinkedHashMap<>();

    /**
     * Registers a callback for one module, or runs it immediately when that module is already ready.
     *
     * <p>Running synchronously on the already-ready path is what removes the missed-signal window: a
     * consumer that registers late must not be punished for it, and there is no earlier point it
     * could have used.</p>
     *
     * @param owner the plugin that owns the callback lifecycle
     * @param moduleName the watched module's plugin name
     * @param callback what to run once that module's data is loaded
     * @param onFailure receives the failure when the synchronous callback throws, may be {@code null}
     * @return a revocable handle; inactive when the arguments are unusable or the callback already ran
     */
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
        // Outside the lock: the callback reads the module it waited for and may call back into
        // CoreLib, so holding this table's monitor while it runs is a deadlock.
        run(entry, key, onFailure);
        return ReadinessRegistration.inactive();
    }

    /**
     * Registers a standing listener for one module, replacing that owner's previous one.
     *
     * <p>Deliberately does <strong>not</strong> invoke the listener when the module is already ready.
     * {@link #whenReady} does, because a one-shot waiter that registered late would otherwise never
     * hear anything; a standing listener has no such window to close, and calling it here would make
     * "registered" and "notified" indistinguishable to the caller.</p>
     *
     * @param owner the plugin that owns the listener lifecycle
     * @param moduleName the watched module's plugin name
     * @param listener what to notify on every transition
     * @return a revocable handle; inactive when the arguments are unusable
     */
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

    /**
     * Marks a module's data as loaded and notifies its waiters.
     *
     * <p>Call this <strong>outside</strong> the module's own readiness lock. The callbacks run
     * synchronously on the calling thread, so publishing from inside a synchronized block or a CAS
     * retry loop would run third-party code while holding module state.</p>
     *
     * @param moduleName the module's plugin name
     * @param onFailure receives the failure when a callback throws, may be {@code null}
     * @return how many callbacks completed without throwing
     */
    public int markReady(@Nullable String moduleName, @Nullable Consumer<Failure> onFailure) {
        if (Texts.isBlank(moduleName)) {
            return 0;
        }
        String key = keyOf(moduleName);
        List<Entry> snapshot;
        boolean becameReady;
        synchronized (this) {
            // Read before write: standing listeners must not be notified when the module was already
            // ready. Several modules publish ready from more than one point in the same reload, and
            // only EmakiItem edge-detects on its own side.
            becameReady = !Boolean.TRUE.equals(states.get(key));
            states.put(key, Boolean.TRUE);
            // Taken and removed in the same critical section: the callbacks then run outside the lock,
            // and a concurrent second markReady must not pick up the same waiters and call them twice.
            List<Entry> pending = waiters.remove(key);
            snapshot = pending == null ? List.of() : List.copyOf(pending);
        }
        // Standing listeners first: a consumer that keeps both wants its cache rebuilt before whatever
        // one-off initialisation reads it.
        if (becameReady) {
            notifyListeners(key, ModuleReadinessPhase.READY, onFailure);
        }
        int succeeded = 0;
        for (Entry entry : snapshot) {
            // A disabled owner is skipped and dropped with the rest: its callback would run against a
            // plugin that is shutting down, which is how one reload becomes a cascade of errors.
            if (entry.owner().isEnabled() && run(entry, key, onFailure)) {
                succeeded++;
            }
        }
        return succeeded;
    }

    /**
     * Marks a module as loading, which is how a reload reports that its data is being replaced.
     *
     * <p>Waiters that have not fired yet are kept: they are waiting for the <em>next</em> ready
     * transition, which is exactly what the reload will produce.</p>
     *
     * <p>Standing listeners are notified so they can invalidate caches <em>before</em> the data is
     * replaced. Like {@link #markReady} this is edge-detected: republishing "loading" while already
     * loading notifies nobody.</p>
     *
     * @param moduleName the module's plugin name
     * @param onFailure receives the failure when a listener throws, may be {@code null}
     */
    public void markLoading(@Nullable String moduleName, @Nullable Consumer<Failure> onFailure) {
        if (Texts.isBlank(moduleName)) {
            return;
        }
        String key = keyOf(moduleName);
        boolean becameLoading;
        synchronized (this) {
            // An absent state counts as a transition: the module is publishing "loading" for the first
            // time, which is exactly what a listener registered before first load is waiting for.
            becameLoading = !Boolean.FALSE.equals(states.get(key));
            states.put(key, Boolean.FALSE);
        }
        if (becameLoading) {
            notifyListeners(key, ModuleReadinessPhase.LOADING, onFailure);
        }
    }

    /**
     * Forgets a module's state, used when it is disabled.
     *
     * <p>Waiters are kept because the module may be enabled again within the same server session,
     * and a consumer that already registered has no way to notice that it needs to re-register.</p>
     *
     * <p>Standing listeners are kept for the same reason and notified once, so a consumer can drop
     * what it cached from a module that is going away. Only published when the module actually had a
     * state to forget.</p>
     *
     * @param moduleName the module's plugin name
     * @param onFailure receives the failure when a listener throws, may be {@code null}
     */
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

    /**
     * Reports whether a module has published a ready state.
     *
     * @param moduleName the module's plugin name
     * @return whether it is currently ready
     */
    public synchronized boolean isReady(@Nullable String moduleName) {
        return !Texts.isBlank(moduleName) && Boolean.TRUE.equals(states.get(keyOf(moduleName)));
    }

    /**
     * Removes every callback owned by one plugin.
     *
     * @param owner the owning plugin
     * @return how many callbacks were removed
     */
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

    /** {@return how many callbacks are currently pending across every module, for diagnostics} */
    public synchronized int pendingCount() {
        int total = 0;
        for (List<Entry> entries : waiters.values()) {
            total += entries.size();
        }
        return total;
    }

    /** Drops every state, waiter and listener. Used when CoreLib itself shuts down. */
    public synchronized void clear() {
        states.clear();
        waiters.clear();
        listeners.clear();
    }

    /** {@return how many standing listeners are registered across every module, for diagnostics} */
    public synchronized int listenerCount() {
        int total = 0;
        for (Map<String, ListenerEntry> owners : listeners.values()) {
            total += owners.size();
        }
        return total;
    }

    /**
     * Notifies one module's standing listeners of a transition.
     *
     * <p>Snapshot under the lock, run outside it: a listener rebuilding its cache reads the module it
     * watched and may call back into CoreLib, so holding this table's monitor while it runs is a
     * deadlock. A disabled owner is skipped and dropped, and one listener throwing does not cost the
     * others their notification.</p>
     *
     * @param moduleKey the already-normalised module key
     * @param phase what to report
     * @param onFailure receives the failure when a listener throws, may be {@code null}
     */
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

    /**
     * Removes one owner's listener for one module, but only when it is still the same entry.
     *
     * <p>The identity check matters: between a handle being closed and this running, the same owner may
     * have registered a replacement, and closing the old handle must not revoke the new listener.</p>
     *
     * @param moduleKey the already-normalised module key
     * @param ownerKey the already-normalised owner key
     * @param expected the entry the caller believes is registered
     */
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

    /**
     * One consumer's pending callback.
     *
     * @param owner the owning plugin
     * @param callback what to run when the watched module becomes ready
     */
    private record Entry(@NotNull Plugin owner, @NotNull Runnable callback) {
    }

    /**
     * One consumer's standing listener.
     *
     * @param owner the owning plugin
     * @param listener what to notify on every transition
     */
    private record ListenerEntry(@NotNull Plugin owner, @NotNull ModuleReadinessListener listener) {
    }

    /**
     * A callback that threw.
     *
     * @param owner name of the owning plugin
     * @param moduleName the module whose readiness triggered it
     * @param error what it threw
     */
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

    /**
     * Handle for a standing listener.
     *
     * <p>Reports {@link #active()} true until closed. A standing listener is never consumed by firing,
     * so unlike a one-shot waiter's handle there is no path where it becomes inactive on its own.</p>
     */
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
