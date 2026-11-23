package emaki.jiuwu.craft.attribute.service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.api.data.DomainSaveResult;
import emaki.jiuwu.craft.corelib.api.data.DomainSnapshot;
import emaki.jiuwu.craft.corelib.api.data.PlayerDataLease;
import emaki.jiuwu.craft.corelib.api.data.PlayerDomainRepository;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import java.util.logging.Level;

public final class ParentAttributeDataStore {

    private static final long DRAIN_TIMEOUT_MS = 5_000L;
    private static final String DATABASE_DOMAIN = "attribute";
    private static final int DATABASE_SCHEMA_VERSION = 1;

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
    private final Map<UUID, Long> databaseRevisions = new ConcurrentHashMap<>();
    private final AtomicBoolean sealed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private volatile AsyncYamlFiles asyncYamlFiles;
    private volatile PlayerDomainRepository databaseRepository;
    private volatile PlayerDataLease databaseLease;

    public ParentAttributeDataStore(EmakiAttributePlugin plugin) {
        this.plugin = plugin;
    }

    public void attachDatabase(PlayerDomainRepository databaseRepository, PlayerDataLease databaseLease) {
        this.databaseRepository = databaseRepository;
        this.databaseLease = databaseLease;
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
            releaseDatabaseLease(uuid);
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
                releaseDatabaseLease(uuid);
                return;
            }
        }
        enqueueSave(ticket).whenComplete((ignored, throwable) -> releaseDatabaseLease(uuid));
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
                FailureRecorder.recordIgnoredFailure("EmakiAttribute", "ParentAttributeDataStore.java#1", exception);
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
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            return acquireDatabaseLease(uuid)
                    .thenCompose(ignored -> repository.load(uuid, DATABASE_DOMAIN))
                    .handle((snapshot, throwable) -> {
                        if (throwable != null) {
                            LogTags.send(plugin, LogTag.PERSISTENCE, Level.WARNING, "persistence.parent_attribute_load_failed", Map.of("player", uuid, "error", String.valueOf(AsyncFailures.unwrapOnce(throwable))));
                            synchronized (entry) {
                                entry.lifecycle = Lifecycle.LOAD_FAILED;
                                return entry.data;
                            }
                        }
                        if (snapshot != null) {
                            databaseRevisions.put(uuid, snapshot.revision());
                        }
                        return installLoaded(uuid, name, entry, decodeDatabasePayload(snapshot));
                    });
        }
        AsyncYamlFiles files = asyncYamlFiles();
        if (files == null) {
            LogTags.send(plugin, LogTag.PERSISTENCE, Level.WARNING, "persistence.parent_attribute_load_unavailable", Map.of("player", uuid));
            synchronized (entry) {
                entry.lifecycle = Lifecycle.LOAD_FAILED;
            }
            return CompletableFuture.completedFuture(entry.data);
        }
        return files.load(file(uuid))
                .handle((section, throwable) -> {
                    if (throwable != null) {
                        LogTags.send(plugin, LogTag.PERSISTENCE, Level.WARNING, "persistence.parent_attribute_load_failed", Map.of("player", uuid, "error", throwable.getMessage()));
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
                LogTags.send(plugin, LogTag.PERSISTENCE, Level.WARNING, "persistence.parent_attribute_modified_while_loading", Map.of("player", uuid));
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
                LogTags.send(plugin, LogTag.PERSISTENCE, Level.WARNING, "persistence.parent_attribute_load_incomplete", Map.of("player", uuid, "error", exception.getMessage()));
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
        PlayerDomainRepository repository = databaseRepository;
        if (repository != null) {
            return writeDatabaseSnapshot(ticket, repository);
        }
        AsyncYamlFiles files = asyncYamlFiles();
        if (files == null) {
            LogTags.send(plugin, LogTag.PERSISTENCE, Level.WARNING, "persistence.parent_attribute_save_unavailable", Map.of("player", ticket.uuid()));
            return CompletableFuture.completedFuture(false);
        }
        return files.save(file(ticket.uuid()), serialize(ticket.snapshot()))
                .handle((ignored, throwable) -> throwable == null);
    }

    private CompletableFuture<Boolean> writeDatabaseSnapshot(SaveTicket ticket, PlayerDomainRepository repository) {
        ParentAttributeData snapshot = ticket.snapshot();
        byte[] payload = YamlFiles.dump(serialize(snapshot)).getBytes(StandardCharsets.UTF_8);
        long expectedRevision = databaseRevisions.getOrDefault(ticket.uuid(), 0L);
        return repository
                .save(ticket.uuid(), DATABASE_DOMAIN, snapshot.name(), payload, DATABASE_SCHEMA_VERSION, expectedRevision)
                .handle((result, throwable) -> {
                    if (throwable == null && result != null
                            && result.outcome() == DomainSaveResult.Outcome.COMMITTED) {
                        databaseRevisions.put(ticket.uuid(), result.revision());
                        return true;
                    }
                    Throwable failure = throwable == null
                            ? new IllegalStateException("数据库保存未提交: "
                                    + (result == null ? "unknown" : result.outcome()))
                            : AsyncFailures.unwrapOnce(throwable);
                    throw new CompletionException(failure);
                });
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
        LogTags.send(plugin, LogTag.PERSISTENCE, Level.WARNING, "persistence.parent_attribute_save_failed", Map.of("player", ticket.uuid(), "error", (throwable == null ? "写入报告失败" : throwable.getMessage())));
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

    private YamlSection decodeDatabasePayload(DomainSnapshot snapshot) {
        if (snapshot == null || snapshot.payload().length == 0) {
            return new MapYamlSection();
        }
        return YamlFiles.load(new String(snapshot.payload(), StandardCharsets.UTF_8));
    }

    private CompletableFuture<Void> acquireDatabaseLease(UUID uuid) {
        PlayerDataLease lease = databaseLease;
        if (lease == null) {
            return CompletableFuture.completedFuture(null);
        }
        return attemptLease(lease, uuid, 5);
    }

    private CompletableFuture<Void> attemptLease(PlayerDataLease lease, UUID uuid, int attemptsLeft) {
        return lease.acquire(uuid).thenCompose(acquired -> {
            if (Boolean.TRUE.equals(acquired)) {
                return CompletableFuture.completedFuture(null);
            }
            if (attemptsLeft <= 1) {
                return CompletableFuture.failedFuture(new IllegalStateException("数据租约获取失败: " + uuid));
            }
            return CompletableFuture.runAsync(() -> { },
                            CompletableFuture.delayedExecutor(2L, TimeUnit.SECONDS))
                    .thenCompose(ignored -> attemptLease(lease, uuid, attemptsLeft - 1));
        });
    }

    private void releaseDatabaseLease(UUID uuid) {
        PlayerDataLease lease = databaseLease;
        if (lease != null) {
            lease.release(uuid);
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
