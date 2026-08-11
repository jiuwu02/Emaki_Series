package emaki.jiuwu.craft.attribute.service;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.ParentAttributeData;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

/**
 * Caches and persists parent attribute allocations.
 *
 * <p>The cache carries explicit load / dirty / save / unload / seal semantics:
 * every session has a monotonic {@code generation}, saves are serialised per
 * player through a {@code SaveLane}, and each save runs against an isolated
 * {@link ParentAttributeData#copy() snapshot} carried by a {@link SaveTicket}.
 * This mirrors {@code PlayerLevelDataCache} on purpose so the two can be
 * converged onto a shared CoreLib template later (P4-A).
 */
public final class ParentAttributeDataStore {

    /** Shutdown drain upper bound. */
    private static final long DRAIN_TIMEOUT_MS = 5_000L;

    enum Lifecycle {
        LOADING,
        ACTIVE,
        CLOSING,
        LOAD_FAILED
    }

    record SaveTicket(UUID uuid,
            Object entryIdentity,
            long generation,
            long revision,
            ParentAttributeData snapshot,
            boolean closeAfterSave) {

        SaveTicket {
            snapshot = snapshot == null ? null : snapshot.copy();
        }

        @Override
        public ParentAttributeData snapshot() {
            return snapshot == null ? null : snapshot.copy();
        }
    }

    /** Outcome of a shutdown drain, reported by {@code AttributeService}. */
    public record DrainReport(boolean drained, int succeeded, int failed, int pending) {

        /** {@code true} when every save finished and none failed. */
        public boolean clean() {
            return drained && failed == 0 && pending == 0;
        }

        @Override
        public String toString() {
            return "drained=" + drained
                    + ", succeeded=" + succeeded
                    + ", failed=" + failed
                    + ", pending=" + pending;
        }
    }

    private final EmakiAttributePlugin plugin;
    private final Map<UUID, SessionEntry> entries = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicLong> generations = new ConcurrentHashMap<>();
    private final Map<UUID, SaveLane> saveLanes = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<ParentAttributeData>> pendingLoads = new ConcurrentHashMap<>();
    private final AtomicBoolean sealed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private volatile AsyncYamlFiles asyncYamlFiles;

    public ParentAttributeDataStore(EmakiAttributePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts a session for {@code player} and loads its file asynchronously.
     *
     * <p>Returns the currently cached data, which may still be the freshly
     * created placeholder while the read is in flight. The join path does not
     * consume the return value, so no caller observes the intermediate state.
     */
    public ParentAttributeData load(Player player) {
        if (player == null) {
            return null;
        }
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        ParentAttributeData data = beginSession(uuid, name);
        if (data == null) {
            return null;
        }
        // Deferred until the read lands: touching the instance now would raise
        // its revision past loadBaselineRevision and make installLoaded treat
        // the join itself as a racing edit, discarding the stored file.
        //
        // Only the name is refreshed here. ensureParentAttributes is left to the
        // main-thread getOrLoad path because it reads AttributeRegistry, whose
        // backing maps are plain LinkedHashMaps and are not safe to touch from
        // the IO thread.
        CompletableFuture<ParentAttributeData> pending = pendingLoads.get(uuid);
        if (pending == null) {
            data.name(name);
            return data;
        }
        pending.thenAccept(loaded -> refreshName(uuid, loaded, name));
        return data;
    }

    /**
     * Starts a session for {@code uuid}, blocking until the file is read.
     *
     * <p>Kept synchronous because callers dereference the result immediately.
     */
    public ParentAttributeData load(UUID uuid, String name) {
        if (uuid == null) {
            return null;
        }
        if (beginSession(uuid, name) == null) {
            // Sealed: never hand back null, callers dereference this directly.
            // The instance stays detached so it can never be persisted.
            return new ParentAttributeData(uuid, name);
        }
        ParentAttributeData data = awaitLoad(uuid);
        if (data == null) {
            return new ParentAttributeData(uuid, name);
        }
        ensureParentAttributes(data);
        return data;
    }

    /**
     * Returns fully loaded data for {@code uuid}, loading it when necessary.
     *
     * <p>Callers dereference the result directly, so this never returns
     * {@code null} for a non-null uuid, and never exposes a session whose file
     * read is still in flight: an in-flight read is awaited first.
     */
    public ParentAttributeData getOrLoad(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        SessionEntry entry = entries.get(uuid);
        if (entry != null) {
            boolean loading;
            ParentAttributeData existing;
            synchronized (entry) {
                loading = entry.lifecycle == Lifecycle.LOADING;
                existing = entry.lifecycle == Lifecycle.CLOSING ? null : entry.data;
            }
            if (existing != null) {
                if (loading) {
                    awaitLoad(uuid);
                }
                ensureParentAttributes(existing);
                return existing;
            }
        }
        // No usable session: either none exists, or the current one is CLOSING.
        Player player = Bukkit.getPlayer(uuid);
        return load(uuid, player == null ? uuid.toString() : player.getName());
    }

    /**
     * Returns the cached instance only when its session is fully loaded.
     *
     * <p>A session whose read is still in flight reports {@code null} so callers
     * fall through to {@link #getOrLoad(UUID)}, which awaits the read instead of
     * computing against an empty placeholder.
     */
    public ParentAttributeData cached(UUID uuid) {
        return uuid == null ? null : activeData(uuid);
    }

    /**
     * Removes {@code uuid} from the cache, optionally flushing it first.
     *
     * <p>The removal and the save snapshot are taken under the entry monitor,
     * so a concurrent mutation cannot slip between them.
     */
    public void unload(UUID uuid, boolean save) {
        if (uuid == null) {
            return;
        }
        pendingLoads.remove(uuid);
        SessionEntry entry = entries.get(uuid);
        if (entry == null) {
            return;
        }
        SaveTicket ticket = null;
        synchronized (entry) {
            entry.lifecycle = Lifecycle.CLOSING;
            if (save) {
                ticket = ticketFor(uuid, entry, true);
            }
            if (ticket == null) {
                entries.remove(uuid, entry);
                saveLanes.remove(uuid);
                return;
            }
        }
        enqueueSave(ticket);
    }

    /** Flushes every dirty entry; returns one future per submitted save. */
    private List<CompletableFuture<Boolean>> saveAllAsync() {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (Map.Entry<UUID, SessionEntry> mapped : entries.entrySet()) {
            SessionEntry entry = mapped.getValue();
            SaveTicket ticket;
            synchronized (entry) {
                ticket = ticketFor(mapped.getKey(), entry, false);
            }
            if (ticket != null) {
                futures.add(enqueueSave(ticket));
            }
        }
        return futures;
    }

    public void saveAll() {
        saveAllAsync();
    }

    /**
     * Seals the cache and drains outstanding saves within a bounded time.
     *
     * <p>After sealing, no new session may start. Waits at most
     * {@value #DRAIN_TIMEOUT_MS} ms in total.
     */
    public DrainReport flushAndSeal() {
        return flushAndSeal(DRAIN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    public DrainReport flushAndSeal(long timeout, TimeUnit unit) {
        synchronized (lifecycleLock) {
            sealed.set(true);
        }
        pendingLoads.clear();
        List<CompletableFuture<Boolean>> futures = saveAllAsync();
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        boolean drained = true;
        int succeeded = 0;
        int failed = 0;
        // Lane tails cover writes that were already in flight when the drain
        // started; those produce no ticket here and would otherwise be missed.
        List<CompletableFuture<Void>> tails = new ArrayList<>();
        for (SaveLane lane : saveLanes.values()) {
            synchronized (lane) {
                tails.add(lane.tail);
            }
        }
        for (CompletableFuture<Boolean> future : futures) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                drained = false;
                break;
            }
            try {
                if (Boolean.TRUE.equals(future.get(remaining, TimeUnit.NANOSECONDS))) {
                    succeeded++;
                } else {
                    failed++;
                }
            } catch (TimeoutException exception) {
                drained = false;
                break;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                drained = false;
                break;
            } catch (ExecutionException exception) {
                failed++;
            }
        }
        for (CompletableFuture<Void> tail : tails) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                drained = false;
                break;
            }
            try {
                tail.get(remaining, TimeUnit.NANOSECONDS);
            } catch (TimeoutException exception) {
                drained = false;
                break;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                drained = false;
                break;
            } catch (ExecutionException exception) {
                // Lane tails absorb failures; the ticket loop above already
                // counted them.
            }
        }
        int pending = 0;
        for (CompletableFuture<Boolean> future : futures) {
            if (!future.isDone()) {
                pending++;
            }
        }
        return new DrainReport(drained && pending == 0, succeeded, failed, pending);
    }

    /**
     * Flushes {@code data} asynchronously when it is the live cached instance.
     *
     * <p>The snapshot is taken under the entry monitor and the write runs off
     * the calling thread; {@code clearDirty} is replaced by
     * {@link ParentAttributeData#markPersisted(long)} against the snapshot
     * revision, so edits made while the write is in flight stay dirty.
     */
    public void save(ParentAttributeData data) {
        if (data == null) {
            return;
        }
        SessionEntry entry = entries.get(data.uuid());
        if (entry == null) {
            return;
        }
        SaveTicket ticket;
        synchronized (entry) {
            if (entry.data != data) {
                return;
            }
            ticket = ticketFor(data.uuid(), entry, false);
        }
        if (ticket != null) {
            enqueueSave(ticket);
        }
    }

    /**
     * Creates or reuses a session entry and triggers the asynchronous read.
     *
     * <p>The sealed check, the generation bump and the {@code entries} insert all
     * happen under {@code lifecycleLock}, so two threads racing on the same uuid
     * cannot produce two entries, two generations or two competing loads.
     *
     * @return the live instance, or {@code null} when the store is sealed.
     */
    private ParentAttributeData beginSession(UUID uuid, String name) {
        SessionEntry created;
        synchronized (lifecycleLock) {
            if (sealed.get()) {
                return null;
            }
            SessionEntry existing = entries.get(uuid);
            if (existing != null) {
                synchronized (existing) {
                    // Only a CLOSING entry is unusable; bumping the generation
                    // below invalidates any save ticket it still owns.
                    if (existing.lifecycle != Lifecycle.CLOSING) {
                        return existing.data;
                    }
                }
            }
            long generation = generations.computeIfAbsent(uuid, ignored -> new AtomicLong()).incrementAndGet();
            created = new SessionEntry(generation, new ParentAttributeData(uuid, name));
            entries.put(uuid, created);
        }
        // Started outside lifecycleLock: the load completion handler needs the
        // entry monitor, and must never wait on a lock held by this thread.
        pendingLoads.put(uuid, startLoad(uuid, name, created));
        synchronized (created) {
            return created.data;
        }
    }

    /**
     * Reads the file off-thread and installs the result into {@code entry}.
     *
     * <p>When the read cannot be performed or fails, the session is marked
     * {@code LOAD_FAILED} instead of {@code ACTIVE}. Saves are then refused, so
     * an empty in-memory placeholder can never overwrite an existing file whose
     * content was never read.
     */
    private CompletableFuture<ParentAttributeData> startLoad(UUID uuid, String name, SessionEntry entry) {
        AsyncYamlFiles files = asyncYamlFiles();
        if (files == null) {
            plugin.getLogger().warning("[ParentAttributeDataStore] CoreLib async YAML service unavailable;"
                    + " parent attribute data for " + uuid
                    + " stays empty and this session is read-only to protect the existing file");
            synchronized (entry) {
                entry.lifecycle = Lifecycle.LOAD_FAILED;
            }
            return CompletableFuture.completedFuture(entry.data);
        }
        return files.load(file(uuid))
                .handle((section, throwable) -> {
                    if (throwable != null) {
                        plugin.getLogger().warning("[ParentAttributeDataStore] Failed to load parent attribute data for "
                                + uuid + "; this session is read-only to protect the existing file: "
                                + throwable.getMessage());
                        synchronized (entry) {
                            entry.lifecycle = Lifecycle.LOAD_FAILED;
                            return entry.data;
                        }
                    }
                    return installLoaded(uuid, name, entry, section);
                });
    }

    /**
     * Applies file content onto the live instance without dropping edits.
     *
     * <p>The live instance is mutated in place rather than replaced, because
     * {@code beginSession} already handed this reference to callers. Content is
     * only applied while the session is still {@code LOADING} and nothing has
     * mutated the instance past {@code loadBaselineRevision} — otherwise a real
     * edit made during the read would be silently overwritten by the file.
     */
    private ParentAttributeData installLoaded(UUID uuid, String name, SessionEntry entry, YamlSection section) {
        synchronized (entry) {
            ParentAttributeData data = entry.data;
            if (entry.lifecycle != Lifecycle.LOADING) {
                return data;
            }
            if (data.revision() > entry.loadBaselineRevision) {
                plugin.getLogger().warning("[ParentAttributeDataStore] Parent attribute data for " + uuid
                        + " was modified while its file was still loading;"
                        + " keeping the in-memory edit and discarding the file content");
                entry.lifecycle = Lifecycle.ACTIVE;
                return data;
            }
            applySection(data, name, section);
            data.clearDirty();
            entry.lifecycle = Lifecycle.ACTIVE;
            return data;
        }
    }

    /** Refreshes the stored player name under the entry monitor. */
    private void refreshName(UUID uuid, ParentAttributeData loaded, String name) {
        if (loaded == null) {
            return;
        }
        SessionEntry entry = entries.get(uuid);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (entry.data == loaded && entry.lifecycle == Lifecycle.ACTIVE) {
                loaded.name(name);
            }
        }
    }

    /**
     * Blocks until the in-flight read for {@code uuid} finished.
     *
     * <p>If the read ended exceptionally the session is still promoted out of
     * {@code LOADING}, so the caller receives the cached instance rather than a
     * detached one that would silently drop its edits.
     */
    private ParentAttributeData awaitLoad(UUID uuid) {
        CompletableFuture<ParentAttributeData> pending = pendingLoads.get(uuid);
        if (pending != null) {
            try {
                pending.join();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("[ParentAttributeDataStore] Load did not complete for "
                        + uuid + ": " + exception.getMessage());
            }
            pendingLoads.remove(uuid, pending);
        }
        SessionEntry entry = entries.get(uuid);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            if (entry.lifecycle == Lifecycle.LOADING) {
                // The read settled without reaching installLoaded (it threw).
                // Treat it as a failed load: readable, but never persisted.
                entry.lifecycle = Lifecycle.LOAD_FAILED;
            }
            return isSettled(entry.lifecycle) ? entry.data : null;
        }
    }

    /**
     * Live instance of a settled, non-closing session.
     *
     * <p>{@code LOAD_FAILED} counts as settled: the instance is handed out so
     * callers keep working against the cached object, while {@link #ticketFor}
     * refuses to persist it.
     */
    private ParentAttributeData activeData(UUID uuid) {
        SessionEntry entry = entries.get(uuid);
        if (entry == null) {
            return null;
        }
        synchronized (entry) {
            return isSettled(entry.lifecycle) ? entry.data : null;
        }
    }

    private static boolean isSettled(Lifecycle lifecycle) {
        return lifecycle == Lifecycle.ACTIVE || lifecycle == Lifecycle.LOAD_FAILED;
    }

    /**
     * Builds a save ticket, or {@code null} when there is nothing to persist.
     *
     * <p>Must be called while holding the {@code entry} monitor. A
     * {@code LOAD_FAILED} session is never persisted, because its in-memory
     * state does not reflect the file content.
     */
    private SaveTicket ticketFor(UUID uuid, SessionEntry entry, boolean closeAfterSave) {
        if (entry.lifecycle == Lifecycle.LOAD_FAILED) {
            return null;
        }
        if (closeAfterSave && entry.closeTicket != null) {
            return entry.closeTicket;
        }
        long revision = entry.data.revision();
        if (revision <= entry.data.persistedRevision()) {
            return null;
        }
        SaveTicket ticket = new SaveTicket(uuid, entry.identity, entry.generation, revision, entry.data, closeAfterSave);
        if (closeAfterSave) {
            entry.closeTicket = ticket;
        }
        return ticket;
    }

    /** Chains the write onto the player's save lane, preserving order. */
    private CompletableFuture<Boolean> enqueueSave(SaveTicket ticket) {
        SaveLane lane = saveLanes.computeIfAbsent(ticket.uuid(), ignored -> new SaveLane());
        CompletableFuture<Boolean> save;
        synchronized (lane) {
            save = lane.tail.handle((ignored, throwable) -> null)
                    .thenCompose(ignored -> writeTicket(ticket))
                    .handle((saved, throwable) -> {
                        boolean ok = throwable == null && Boolean.TRUE.equals(saved);
                        if (ok) {
                            commitSaved(ticket);
                        } else {
                            commitFailed(ticket, throwable);
                        }
                        return ok;
                    });
            lane.tail = save.handle((ignored, throwable) -> null);
        }
        return save;
    }

    private CompletableFuture<Boolean> writeTicket(SaveTicket ticket) {
        AsyncYamlFiles files = asyncYamlFiles();
        if (files == null) {
            plugin.getLogger().warning("[ParentAttributeDataStore] CoreLib async YAML service unavailable;"
                    + " dropped save for " + ticket.uuid());
            return CompletableFuture.completedFuture(false);
        }
        return files.save(file(ticket.uuid()), serialize(ticket.snapshot()))
                .handle((ignored, throwable) -> throwable == null);
    }

    /**
     * Marks the saved revision as persisted, ignoring stale tickets.
     *
     * <p>A ticket whose generation or entry identity no longer matches belongs to
     * a superseded session and must not touch the current one.
     */
    private void commitSaved(SaveTicket ticket) {
        SessionEntry entry = entries.get(ticket.uuid());
        if (entry == null) {
            return;
        }
        boolean remove = false;
        synchronized (entry) {
            if (!matches(entry, ticket)) {
                return;
            }
            entry.data.markPersisted(ticket.revision());
            if (ticket.closeAfterSave() && entry.lifecycle == Lifecycle.CLOSING) {
                remove = true;
            }
        }
        if (remove) {
            entries.remove(ticket.uuid(), entry);
            saveLanes.remove(ticket.uuid());
        }
    }

    /**
     * Logs the failure and keeps the entry dirty so a later save can retry.
     *
     * <p>{@code markPersisted} is deliberately not called: the data never
     * reached disk, so it must stay dirty.
     */
    private void commitFailed(SaveTicket ticket, Throwable throwable) {
        plugin.getLogger().warning("[ParentAttributeDataStore] Failed to save parent attribute data for "
                + ticket.uuid() + ": " + (throwable == null ? "write reported failure" : throwable.getMessage()));
        SessionEntry entry = entries.get(ticket.uuid());
        if (entry == null) {
            return;
        }
        boolean remove = false;
        synchronized (entry) {
            if (!matches(entry, ticket)) {
                return;
            }
            // Identity, not equality: SaveTicket copies its snapshot on every
            // access, so two logically equal tickets never compare equal.
            if (entry.closeTicket == ticket) {
                entry.closeTicket = null;
                remove = entry.lifecycle == Lifecycle.CLOSING;
            }
        }
        if (remove) {
            entries.remove(ticket.uuid(), entry);
            saveLanes.remove(ticket.uuid());
        }
    }

    private boolean matches(SessionEntry entry, SaveTicket ticket) {
        return entry != null
                && ticket != null
                && entry.identity == ticket.entryIdentity()
                && entry.generation == ticket.generation();
    }

    /** Builds the YAML body; key order is identical to the previous writer. */
    private Map<String, Object> serialize(ParentAttributeData data) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", 1);
        root.put("uuid", data.uuid().toString());
        root.put("name", data.name());
        root.put("available_points", data.availablePoints());
        root.put("reset_points", data.resetPoints());
        Map<String, Object> allocations = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : data.allocations().entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                allocations.put(entry.getKey(), entry.getValue());
            }
        }
        root.put("allocations", allocations);
        root.put("updated_at", data.updatedAt());
        return root;
    }

    private void applySection(ParentAttributeData data, String name, YamlSection root) {
        if (root == null) {
            return;
        }
        String storedName = root.getString("name", null);
        data.name(Texts.isBlank(storedName) ? name : root.getString("name", name));
        data.availablePoints(Math.max(0, root.getInt("available_points", 0)));
        data.resetPoints(Math.max(0, root.getInt("reset_points", 0)));
        YamlSection allocations = root.getSection("allocations");
        if (allocations == null) {
            return;
        }
        for (String key : allocations.getKeys(false)) {
            String id = Texts.normalizeId(key);
            int points = Math.max(0, allocations.getInt(key, 0));
            if (Texts.isNotBlank(id) && points > 0) {
                data.putAllocation(id, points);
            }
        }
    }

    private void ensureParentAttributes(ParentAttributeData data) {
        if (data == null || plugin.attributeRegistry() == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : data.allocations().entrySet()) {
            AttributeDefinition definition = plugin.attributeRegistry().get(entry.getKey());
            boolean remove = definition == null
                    || !definition.parentAttribute()
                    || entry.getValue() == null
                    || entry.getValue() <= 0;
            if (remove) {
                data.removeAllocation(entry.getKey());
            }
        }
    }

    /** Lazily resolves CoreLib; {@code null} when it is not ready yet. */
    private AsyncYamlFiles asyncYamlFiles() {
        AsyncYamlFiles resolved = asyncYamlFiles;
        if (resolved != null) {
            return resolved;
        }
        var coreLib = plugin.coreLib();
        if (coreLib == null) {
            return null;
        }
        resolved = coreLib.asyncYamlFiles(plugin);
        asyncYamlFiles = resolved;
        return resolved;
    }

    private File file(UUID uuid) {
        return plugin.getDataFolder().toPath().resolve("data").resolve("parent_attributes").resolve(uuid + ".yml").toFile();
    }

    private static final class SaveLane {

        private CompletableFuture<Void> tail = CompletableFuture.completedFuture(null);
    }

    private static final class SessionEntry {

        private final Object identity = new Object();
        private final long generation;
        private final ParentAttributeData data;
        /**
         * Revision of {@link #data} at the moment the load was started. A higher
         * revision when the read lands means a real edit raced the load.
         */
        private final long loadBaselineRevision;
        private Lifecycle lifecycle = Lifecycle.LOADING;
        private SaveTicket closeTicket;

        private SessionEntry(long generation, ParentAttributeData data) {
            this.generation = generation;
            this.data = data;
            this.loadBaselineRevision = data.revision();
        }
    }
}
