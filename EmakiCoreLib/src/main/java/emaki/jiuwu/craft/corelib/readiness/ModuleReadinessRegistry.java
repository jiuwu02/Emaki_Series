package emaki.jiuwu.craft.corelib.readiness;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
 */
public final class ModuleReadinessRegistry {

    private final Map<String, Boolean> states = new LinkedHashMap<>();
    private final Map<String, List<Entry>> waiters = new LinkedHashMap<>();

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
        synchronized (this) {
            states.put(key, Boolean.TRUE);
            // Taken and removed in the same critical section: the callbacks then run outside the lock,
            // and a concurrent second markReady must not pick up the same waiters and call them twice.
            List<Entry> pending = waiters.remove(key);
            if (pending == null || pending.isEmpty()) {
                return 0;
            }
            snapshot = List.copyOf(pending);
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
     * @param moduleName the module's plugin name
     */
    public void markLoading(@Nullable String moduleName) {
        if (Texts.isBlank(moduleName)) {
            return;
        }
        synchronized (this) {
            states.put(keyOf(moduleName), Boolean.FALSE);
        }
    }

    /**
     * Forgets a module's state, used when it is disabled.
     *
     * <p>Waiters are kept because the module may be enabled again within the same server session,
     * and a consumer that already registered has no way to notice that it needs to re-register.</p>
     *
     * @param moduleName the module's plugin name
     */
    public void markAbsent(@Nullable String moduleName) {
        if (Texts.isBlank(moduleName)) {
            return;
        }
        synchronized (this) {
            states.remove(keyOf(moduleName));
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

    /** Drops every state and callback. Used when CoreLib itself shuts down. */
    public synchronized void clear() {
        states.clear();
        waiters.clear();
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
}
