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

public final class ParentAttributeDataStore {

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

    public record DrainReport(boolean drained, int succeeded, int failed, int pending) {

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

        CompletableFuture<ParentAttributeData> pending = pendingLoads.get(uuid);
        if (pending == null) {
            data.name(name);
            return data;
        }
        pending.thenAccept(loaded -> refreshName(uuid, loaded, name));
        return data;
    }

    public ParentAttributeData load(UUID uuid, String name) {
        if (uuid == null) {
            return null;
        }
        if (beginSession(uuid, name) == null) {

            return new ParentAttributeData(uuid, name);
        }
        ParentAttributeData data = awaitLoad(uuid);
        if (data == null) {
            return new ParentAttributeData(uuid, name);
        }
        ensureParentAttributes(data);
        return data;
    }

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

        Player player = Bukkit.getPlayer(uuid);
        return load(uuid, player == null ? uuid.toString() : player.getName());
    }

    public ParentAttributeData cached(UUID uuid) {
        return uuid == null ? null : activeData(uuid);
    }

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

    private ParentAttributeData beginSession(UUID uuid, String name) {
        SessionEntry created;
        synchronized (lifecycleLock) {
            if (sealed.get()) {
                return null;
            }
            SessionEntry existing = entries.get(uuid);
            if (existing != null) {
                synchronized (existing) {

                    if (existing.lifecycle != Lifecycle.CLOSING) {
                        return existing.data;
                    }
                }
            }
            long generation = generations.computeIfAbsent(uuid, ignored -> new AtomicLong()).incrementAndGet();
            created = new SessionEntry(generation, new ParentAttributeData(uuid, name));
            entries.put(uuid, created);
        }

        pendingLoads.put(uuid, startLoad(uuid, name, created));
        synchronized (created) {
            return created.data;
        }
    }

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

                entry.lifecycle = Lifecycle.LOAD_FAILED;
            }
            return isSettled(entry.lifecycle) ? entry.data : null;
        }
    }

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
