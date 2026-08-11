package emaki.jiuwu.craft.corelib.action.pipeline.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Per-owner callbacks re-run when the stage table is rebuilt.
 *
 * <p>A CoreLib reload builds a fresh {@link StageRegistry} and retires the previous one. Stages that a
 * business module registered once at {@code onEnable} would therefore vanish after the first reload, with
 * no diagnostic beyond an eventual {@code unknown_stage}. This table is how a module asks to be called
 * again so it can re-register against the new table.</p>
 *
 * <p>Deliberately not a field on the plugin class: keeping it a plain object that only needs
 * {@link Plugin} makes the rebuild contract testable without a running server.</p>
 */
public final class StageRebuildListeners {

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    /**
     * Registers or replaces the callback for one owner.
     *
     * <p>Replacing rather than accumulating is what makes a module's {@code onEnable} safe to run twice: a
     * second registration for the same owner must not cause the stages to be registered twice, because the
     * second attempt would fail on duplicate ids.</p>
     *
     * @param owner the owning plugin
     * @param reregister the routine that re-registers that owner's stages
     * @return whether the callback was accepted
     */
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

    /**
     * Removes an owner's callback.
     *
     * @param owner the owning plugin
     * @return whether a callback was removed
     */
    public synchronized boolean remove(@Nullable Plugin owner) {
        return owner != null && entries.remove(keyOf(owner)) != null;
    }

    /** {@return how many owners currently have a callback} */
    public synchronized int size() {
        return entries.size();
    }

    /** Drops every callback. Used when CoreLib itself shuts down. */
    public synchronized void clear() {
        entries.clear();
    }

    /**
     * Runs every registered callback.
     *
     * <p>Callbacks run outside the lock so that a module re-registering its stages, which calls back into
     * CoreLib, cannot deadlock against this table.</p>
     *
     * <p>A disabled owner is skipped and dropped: its stages were revoked with it, and calling into a
     * plugin that is shutting down is how reload turns into a cascade of unrelated errors. A callback that
     * throws is reported through {@code onFailure} and does not stop the remaining owners, because one
     * module's broken registration must not cost every other module its stages.</p>
     *
     * @param onFailure receives the owner name and the failure, may be {@code null}
     * @return how many callbacks completed without throwing
     */
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

    /**
     * One owner's rebuild callback.
     *
     * @param owner the owning plugin
     * @param reregister its registration routine
     */
    private record Entry(@NotNull Plugin owner, @NotNull Runnable reregister) {
    }

    /**
     * A callback that threw.
     *
     * @param owner name of the owning plugin
     * @param error what it threw
     */
    public record Failure(@NotNull String owner, @NotNull Throwable error) {
    }
}
