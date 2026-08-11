package emaki.jiuwu.craft.cooking.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.model.NutritionTypeConfig;
import emaki.jiuwu.craft.cooking.model.PlayerNutritionData;






public final class PlayerNutritionDataStore {

    private static final int SCHEMA_VERSION = 1;

    public record FlushResult(
            boolean logicalSavesCompleted,
            boolean fileScopeDrained,
            int pendingFileOperations,
            int remainingDirtyEntries,
            List<Throwable> failures) {

        public FlushResult {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        public boolean success() {
            return logicalSavesCompleted
                    && fileScopeDrained
                    && pendingFileOperations == 0
                    && remainingDirtyEntries == 0
                    && failures.isEmpty();
        }
    }

    private final File dataFolder;
    private final Logger logger;
    private final Supplier<AsyncYamlFiles> asyncYamlFilesSupplier;
    private final PlayerNutritionDataCache cache;

    public PlayerNutritionDataStore(JavaPlugin plugin, Supplier<AsyncYamlFiles> asyncYamlFilesSupplier) {
        this(plugin.getDataFolder(), plugin.getLogger(), asyncYamlFilesSupplier, new PlayerNutritionDataCache());
    }

    PlayerNutritionDataStore(
            File dataFolder,
            Logger logger,
            Supplier<AsyncYamlFiles> asyncYamlFilesSupplier) {
        this(dataFolder, logger, asyncYamlFilesSupplier, new PlayerNutritionDataCache());
    }

    PlayerNutritionDataStore(
            File dataFolder,
            Logger logger,
            Supplier<AsyncYamlFiles> asyncYamlFilesSupplier,
            PlayerNutritionDataCache cache) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.asyncYamlFilesSupplier = asyncYamlFilesSupplier;
        this.cache = cache;
    }

    public CompletableFuture<PlayerNutritionData> beginSession(
            Player player,
            Map<String, NutritionTypeConfig> types) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        return beginSession(player.getUniqueId(), player.getName(), types, true);
    }

    public CompletableFuture<PlayerNutritionData> beginSession(
            OfflinePlayer player,
            Map<String, NutritionTypeConfig> types) {
        if (player == null) {
            return CompletableFuture.completedFuture(null);
        }
        return beginSession(player.getUniqueId(), player.getName(), types, false);
    }

    CompletableFuture<PlayerNutritionData> beginSession(
            UUID uuid,
            String name,
            Map<String, NutritionTypeConfig> types,
            boolean retryFailed) {
        if (uuid == null || cache.sealed()) {
            return CompletableFuture.completedFuture(null);
        }
        PlayerNutritionData existing = cache.activeData(uuid);
        if (existing != null) {
            PlayerNutritionDataCache.SessionTicket current = cache.currentTicket(uuid);
            if (current != null) {
                String resolvedName = resolveName(name);
                mutate(uuid, current.generation(), types, data -> {
                    if (!resolvedName.equals(data.name())) {
                        data.name(resolvedName);
                    }
                    ensureTypes(data, types);
                    return null;
                });
                return CompletableFuture.completedFuture(cache.activeData(uuid));
            }
        }

        PlayerNutritionData fallback = defaults(uuid, resolveName(name), types);
        PlayerNutritionDataCache.SessionTicket ticket = cache.beginSession(uuid, fallback, retryFailed);
        if (ticket == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (cache.isActiveGeneration(uuid, ticket.generation())) {
            mutate(uuid, ticket.generation(), types, data -> {
                String resolvedName = resolveName(name);
                if (!resolvedName.equals(data.name())) {
                    data.name(resolvedName);
                }
                ensureTypes(data, types);
                return null;
            });
            return CompletableFuture.completedFuture(cache.activeData(uuid));
        }
        PlayerNutritionDataCache.Snapshot existingSnapshot = cache.snapshot(uuid);
        if (existingSnapshot != null
                && existingSnapshot.generation() == ticket.generation()
                && existingSnapshot.state() == PlayerNutritionDataCache.SessionState.FAILED
                && !retryFailed) {
            return CompletableFuture.completedFuture(null);
        }
        return cache.waitForLane(uuid)
                .thenCompose(_ -> physicalLoad(uuid, resolveName(name), types))
                .handle((loaded, throwable) -> {
                    if (throwable != null) {
                        cache.installLoadFailure(ticket, fallback);
                        throw new CompletionException(AsyncFailures.unwrap(throwable));
                    }
                    PlayerNutritionDataCache.CommitResult result = cache.installLoaded(ticket, loaded);
                    if (result != PlayerNutritionDataCache.CommitResult.COMMITTED) {
                        return null;
                    }
                    return cache.activeData(uuid);
                });
    }




    public PlayerNutritionData getOrLoad(OfflinePlayer player, Map<String, NutritionTypeConfig> types) {
        return player == null ? null : cached(player.getUniqueId());
    }

    public CompletableFuture<PlayerNutritionData> getOrLoadAsync(
            UUID uuid,
            Map<String, NutritionTypeConfig> types) {
        return getOrLoadAsync(uuid, null, types);
    }

    public CompletableFuture<PlayerNutritionData> getOrLoadAsync(
            UUID uuid,
            String name,
            Map<String, NutritionTypeConfig> types) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        PlayerNutritionData cached = cached(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return beginSession(uuid, name, types, false);
    }

    public void load(Player player, Map<String, NutritionTypeConfig> types) {
        beginSession(player, types).whenComplete((_, throwable) -> {
            if (throwable != null && player != null) {
                logLoadFailure(player.getUniqueId(), throwable);
            }
        });
    }

    public CompletableFuture<Boolean> saveAsync(UUID uuid) {
        PlayerNutritionDataCache.SessionTicket ticket = cache.currentTicket(uuid);
        if (ticket == null) {
            return CompletableFuture.completedFuture(false);
        }
        return saveAsync(uuid, ticket.generation(), false);
    }

    public CompletableFuture<Boolean> saveAsync(UUID uuid, long generation, boolean closeAfterSave) {
        PlayerNutritionDataCache.SaveTicket ticket = cache.snapshotForSave(uuid, generation, closeAfterSave);
        if (ticket == null) {
            return CompletableFuture.completedFuture(false);
        }
        return cache.enqueueSave(ticket, this::physicalSave)
                .whenComplete((_, throwable) -> {
                    if (throwable != null) {
                        logSaveFailure(uuid, throwable);
                    }
                });
    }

    public void save(UUID uuid) {
        saveAsync(uuid);
    }

    public CompletableFuture<Boolean> unloadAsync(UUID uuid, long generation, boolean save) {
        if (!save) {
            return CompletableFuture.completedFuture(cache.discard(uuid, generation));
        }
        return saveAsync(uuid, generation, true);
    }

    public void unload(UUID uuid, boolean save) {
        long generation = currentGeneration(uuid);
        if (generation >= 0L) {
            unloadAsync(uuid, generation, save);
        }
    }

    public CompletableFuture<Void> saveAllAsync() {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (PlayerNutritionDataCache.SaveTicket ticket : cache.dirtySnapshots()) {
            futures.add(cache.enqueueSave(ticket, this::physicalSave)
                    .whenComplete((_, throwable) -> {
                        if (throwable != null) {
                            logSaveFailure(ticket.uuid(), throwable);
                        }
                    }));
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public void saveAll() {
        saveAllAsync();
    }

    public FlushResult flushAndSeal(long timeout, TimeUnit unit) {
        if (unit == null) {
            unit = TimeUnit.SECONDS;
        }
        cache.seal();
        List<Throwable> failures = new ArrayList<>();
        List<CompletableFuture<Boolean>> saves = new ArrayList<>();
        for (PlayerNutritionDataCache.SaveTicket ticket : cache.dirtySnapshots()) {
            CompletableFuture<Boolean> future = cache.enqueueSave(ticket, this::physicalSave)
                    .whenComplete((_, throwable) -> {
                        if (throwable != null) {
                            logSaveFailure(ticket.uuid(), throwable);
                        }
                    });
            saves.add(future);
        }

        boolean logicalCompleted = false;
        long timeoutNanos = Math.max(0L, unit.toNanos(timeout));
        long deadline = System.nanoTime() + timeoutNanos;
        try {
            CompletableFuture.allOf(saves.toArray(CompletableFuture[]::new))
                    .get(Math.max(1L, timeoutNanos), TimeUnit.NANOSECONDS);
            logicalCompleted = true;
        } catch (Throwable throwable) {
            failures.add(AsyncFailures.unwrap(throwable));
        }

        AsyncFileService.DrainResult drainResult = new AsyncFileService.DrainResult(true, 0, List.of());
        AsyncYamlFiles asyncFiles = asyncYamlFiles();
        if (asyncFiles != null) {
            long remaining = Math.max(1L, deadline - System.nanoTime());
            drainResult = asyncFiles.sealAndDrain(remaining, TimeUnit.NANOSECONDS);
            failures.addAll(drainResult.failures());
        }
        return new FlushResult(
                logicalCompleted,
                drainResult.drained(),
                drainResult.pendingOperations(),
                cache.dirtyCount(),
                failures
        );
    }

    public PlayerNutritionData cached(UUID uuid) {
        return cache.activeData(uuid);
    }

    public PlayerNutritionData visibleCached(UUID uuid) {
        return cache.visibleData(uuid);
    }

    public long currentGeneration(UUID uuid) {
        return cache.generation(uuid);
    }

    public boolean isCurrentGeneration(UUID uuid, long generation) {
        return cache.isCurrentGeneration(uuid, generation);
    }

    public boolean isKnownGeneration(UUID uuid, long generation) {
        return cache.isKnownGeneration(uuid, generation);
    }

    public <T> T mutate(
            UUID uuid,
            long generation,
            Map<String, NutritionTypeConfig> types,
            Function<PlayerNutritionData, T> mutation) {
        if (mutation == null) {
            return null;
        }
        return cache.mutate(uuid, generation, data -> {
            ensureTypes(data, types);
            return mutation.apply(data);
        });
    }

    public <T> T mutateActive(
            UUID uuid,
            Map<String, NutritionTypeConfig> types,
            Function<PlayerNutritionData, T> mutation) {
        PlayerNutritionDataCache.SessionTicket ticket = cache.currentTicket(uuid);
        if (ticket == null) {
            return null;
        }
        return mutate(uuid, ticket.generation(), types, mutation);
    }

    public void ensureTypesForCached(Map<String, NutritionTypeConfig> types) {
        for (PlayerNutritionDataCache.Snapshot snapshot : cache.snapshots()) {
            if (snapshot.state() == PlayerNutritionDataCache.SessionState.ACTIVE) {
                mutate(snapshot.uuid(), snapshot.generation(), types, data -> {
                    ensureTypes(data, types);
                    return null;
                });
            }
        }
    }

    public int dirtyCount() {
        return cache.dirtyCount();
    }

    private CompletableFuture<PlayerNutritionData> physicalLoad(
            UUID uuid,
            String name,
            Map<String, NutritionTypeConfig> types) {
        File file = file(uuid);
        AsyncYamlFiles asyncFiles = asyncYamlFiles();
        if (asyncFiles == null) {
            return synchronousFuture(() -> loadFile(file, uuid, name, types));
        }
        return asyncFiles.load(file)
                .thenApply(root -> deserialize(root, uuid, name, types));
    }

    private CompletableFuture<Boolean> physicalSave(PlayerNutritionDataCache.SaveTicket ticket) {
        Map<String, Object> serialized = serialize(ticket.snapshot());
        File file = file(ticket.uuid());
        AsyncYamlFiles asyncFiles = asyncYamlFiles();
        if (asyncFiles == null) {
            try {
                YamlFiles.save(file, serialized);
                return CompletableFuture.completedFuture(true);
            } catch (IOException exception) {
                return CompletableFuture.failedFuture(exception);
            }
        }
        return asyncFiles.save(file, serialized)
                .thenApply(_ -> true);
    }

    private PlayerNutritionData loadFile(
            File file,
            UUID uuid,
            String name,
            Map<String, NutritionTypeConfig> types) {
        YamlSection root = YamlFiles.load(file);
        return deserialize(root, uuid, name, types);
    }

    private PlayerNutritionData deserialize(
            YamlSection root,
            UUID uuid,
            String name,
            Map<String, NutritionTypeConfig> types) {
        PlayerNutritionData data = new PlayerNutritionData(uuid, root.getString("name", resolveName(name)));
        YamlSection values = root.getSection("nutrition");
        if (values != null) {
            for (String typeId : values.getKeys(false)) {
                NutritionTypeConfig type = types == null ? null : types.get(typeId);
                double max = type == null ? Double.MAX_VALUE : type.max();
                double min = type == null ? 0D : type.min();
                data.set(typeId, clamp(values.getDouble(typeId, 0D), min, max));
            }
        }
        ensureTypes(data, types);
        data.clearDirty();
        return data;
    }

    private Map<String, Object> serialize(PlayerNutritionData data) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", SCHEMA_VERSION);
        root.put("uuid", data.uuid().toString());
        root.put("name", data.name());
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : data.values().entrySet()) {
            values.put(entry.getKey(), entry.getValue());
        }
        root.put("nutrition", values);
        return root;
    }

    private PlayerNutritionData defaults(
            UUID uuid,
            String name,
            Map<String, NutritionTypeConfig> types) {
        PlayerNutritionData data = new PlayerNutritionData(uuid, name);
        ensureTypes(data, types);
        data.clearDirty();
        return data;
    }

    private void ensureTypes(PlayerNutritionData data, Map<String, NutritionTypeConfig> types) {
        if (types == null || types.isEmpty()) {
            return;
        }
        for (NutritionTypeConfig type : types.values()) {
            if (type != null && !data.has(type.id())) {
                data.set(type.id(), type.defaultValue());
            }
        }
    }

    private File file(UUID uuid) {
        return new File(dataDirectory(), uuid + ".yml");
    }

    private File dataDirectory() {
        return new File(dataFolder, "data/nutrition");
    }

    private AsyncYamlFiles asyncYamlFiles() {
        if (asyncYamlFilesSupplier == null) {
            return null;
        }
        try {
            return asyncYamlFilesSupplier.get();
        } catch (RuntimeException exception) {
            if (cache.sealed() || exception instanceof RejectedExecutionException) {
                return null;
            }
            throw exception;
        }
    }

    private <T> CompletableFuture<T> synchronousFuture(Supplier<T> supplier) {
        try {
            return CompletableFuture.completedFuture(supplier.get());
        } catch (Throwable throwable) {
            return CompletableFuture.failedFuture(throwable);
        }
    }

    private String resolveName(String name) {
        return name == null || name.isBlank() ? "Unknown" : name;
    }

    private double clamp(double value, double min, double max) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private void logLoadFailure(UUID uuid, Throwable throwable) {
        logger.log(Level.WARNING, "Failed to load nutrition data for " + uuid, AsyncFailures.unwrap(throwable));
    }

    private void logSaveFailure(UUID uuid, Throwable throwable) {
        logger.log(Level.WARNING, "Failed to save nutrition data for " + uuid, AsyncFailures.unwrap(throwable));
    }
}
