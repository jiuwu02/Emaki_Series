package emaki.jiuwu.craft.cooking.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.async.AsyncFailures;
import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.DrainResult;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.FileScope;
import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.CommitMode;
import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.Semantics;
import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.Status;
import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.Unit;
import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.UnitKind;
import emaki.jiuwu.craft.cooking.service.CookingCompletionOperation.UnitState;




public final class CookingCompletionJournalStore {

    private static final int SCHEMA_VERSION = 1;
    private static final Comparator<CookingCompletionOperation> OPERATION_ORDER = Comparator
            .comparingLong(CookingCompletionOperation::createdAtMs)
            .thenComparing(CookingCompletionOperation::operationId);

    private final Logger logger;
    private final FileScope fileScope;
    private final Path activeDirectory;
    private final Path archiveDirectory;
    private final Path quarantineDirectory;
    private final ConcurrentMap<String, CookingCompletionOperation> activeById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CookingCompletionOperation> activeByCompletionKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<Path, Object> synchronousPathLocks = new ConcurrentHashMap<>();
    private final Object indexLock = new Object();
    private final AtomicBoolean sealed = new AtomicBoolean(false);

    public CookingCompletionJournalStore(JavaPlugin plugin) {
        this(plugin, (FileScope) null);
    }

    public CookingCompletionJournalStore(JavaPlugin plugin, FileScope fileScope) {
        this(
                Objects.requireNonNull(plugin, "plugin").getDataFolder(),
                plugin.getLogger(),
                fileScope
        );
    }

    public CookingCompletionJournalStore(JavaPlugin plugin, AsyncFileService asyncFileService) {
        this(plugin, asyncFileService == null ? null : asyncFileService.defaultScope());
    }

    public CookingCompletionJournalStore(File dataFolder, Logger logger, FileScope fileScope) {
        Path dataRoot = Objects.requireNonNull(dataFolder, "dataFolder").toPath().resolve("data/completions");
        this.logger = logger == null ? Logger.getLogger(CookingCompletionJournalStore.class.getName()) : logger;
        this.fileScope = fileScope;
        this.activeDirectory = dataRoot.resolve("active");
        this.archiveDirectory = dataRoot.resolve("archive");
        this.quarantineDirectory = dataRoot.resolve("quarantine");
    }

    public CompletableFuture<List<CookingCompletionOperation>> loadActive() {
        if (sealed.get()) {
            return rejectedFuture();
        }
        return readIo(activeDirectory, "cooking-completion-list", this::listActiveFiles)
                .thenCompose(this::loadFiles)
                .thenCompose(this::installLoadedOperations);
    }

    public CompletableFuture<CookingCompletionOperation> createIfAbsent(CookingCompletionOperation operation) {
        Objects.requireNonNull(operation, "operation");
        if (sealed.get()) {
            return rejectedFuture();
        }

        synchronized (indexLock) {
            CookingCompletionOperation existing = activeByCompletionKey.get(operation.completionKey());
            if (existing != null && !existing.isTerminal()) {
                return CompletableFuture.completedFuture(existing);
            }
            CookingCompletionOperation sameId = activeById.get(operation.operationId());
            if (sameId != null) {
                return CompletableFuture.completedFuture(sameId);
            }
            activeById.put(operation.operationId(), operation);
            indexNonTerminal(operation);
        }

        Path path = activePath(operation.operationId());
        CompletableFuture<CookingCompletionOperation> created = writeOperation(path, operation, "cooking-completion-create")
                .thenApply(_ -> operation);
        return created.whenComplete((_, throwable) -> {
            if (throwable != null) {
                removeFromIndex(operation);
            }
        });
    }

    public CompletableFuture<CookingCompletionOperation> save(CookingCompletionOperation operation) {
        Objects.requireNonNull(operation, "operation");
        if (sealed.get()) {
            return rejectedFuture();
        }

        CookingCompletionOperation previous;
        synchronized (indexLock) {
            CookingCompletionOperation conflict = activeByCompletionKey.get(operation.completionKey());
            if (!operation.isTerminal()
                    && conflict != null
                    && !conflict.operationId().equals(operation.operationId())) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Completion key already has an active operation: " + operation.completionKey()));
            }
            previous = activeById.put(operation.operationId(), operation);
            if (previous != null) {
                removeCompletionKeyIndex(previous);
            }
            indexNonTerminal(operation);
        }

        Path path = activePath(operation.operationId());
        CompletableFuture<CookingCompletionOperation> saved = writeOperation(path, operation, "cooking-completion-save")
                .thenApply(_ -> operation);
        return saved.whenComplete((_, throwable) -> {
            if (throwable != null) {
                restoreIndex(operation, previous);
            }
        });
    }

    public CompletableFuture<CookingCompletionOperation> archive(CookingCompletionOperation operation) {
        Objects.requireNonNull(operation, "operation");
        return moveFromActive(operation, archivePath(operation.operationId()), "cooking-completion-archive");
    }

    public CompletableFuture<CookingCompletionOperation> quarantine(CookingCompletionOperation operation) {
        Objects.requireNonNull(operation, "operation");
        CookingCompletionOperation quarantined = operation.status() == Status.QUARANTINED
                ? operation
                : operation.withStatus(Status.QUARANTINED);
        return moveFromActive(quarantined, quarantinePath(quarantined.operationId()), "cooking-completion-quarantine");
    }

    public CompletableFuture<CookingCompletionOperation> quarantine(
            CookingCompletionOperation operation,
            String error) {
        Objects.requireNonNull(operation, "operation");
        return quarantine(operation.withError(error));
    }

    public List<CookingCompletionOperation> activeOperations() {
        return activeById.values().stream()
                .sorted(OPERATION_ORDER)
                .toList();
    }

    public Optional<CookingCompletionOperation> findActiveByCompletionKey(String completionKey) {
        if (completionKey == null || completionKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(activeByCompletionKey.get(completionKey.trim()));
    }

    public CompletableFuture<Void> waitForIdle() {
        return fileScope == null
                ? CompletableFuture.completedFuture(null)
                : fileScope.waitForIdle();
    }

    public DrainResult sealAndDrain(long timeout, TimeUnit unit) {
        sealed.set(true);
        TimeUnit resolvedUnit = unit == null ? TimeUnit.SECONDS : unit;
        return fileScope == null
                ? new DrainResult(true, 0, List.of())
                : fileScope.sealAndDrain(Math.max(1L, timeout), resolvedUnit);
    }

    private CompletableFuture<List<LoadedOperation>> loadFiles(List<Path> paths) {
        if (paths.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<CompletableFuture<LoadedOperation>> loads = new ArrayList<>(paths.size());
        for (Path path : paths) {
            loads.add(readIo(path, "cooking-completion-load:" + path.getFileName(), () -> loadOne(path)));
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
                .thenApply(_ -> loads.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .toList());
    }

    private CompletableFuture<List<CookingCompletionOperation>> installLoadedOperations(List<LoadedOperation> loaded) {
        List<LoadedOperation> ordered = loaded.stream()
                .sorted(Comparator.comparing(entry -> entry.operation(), OPERATION_ORDER))
                .toList();
        List<LoadedOperation> duplicates = new ArrayList<>();
        Map<String, CookingCompletionOperation> byId = new LinkedHashMap<>();
        Map<String, CookingCompletionOperation> byKey = new LinkedHashMap<>();

        for (LoadedOperation entry : ordered) {
            CookingCompletionOperation operation = entry.operation();
            if (byId.containsKey(operation.operationId())) {
                duplicates.add(entry);
                continue;
            }
            if (!operation.isTerminal() && byKey.containsKey(operation.completionKey())) {
                duplicates.add(entry);
                continue;
            }
            byId.put(operation.operationId(), operation);
            if (!operation.isTerminal()) {
                byKey.put(operation.completionKey(), operation);
            }
        }

        synchronized (indexLock) {
            activeById.clear();
            activeById.putAll(byId);
            activeByCompletionKey.clear();
            activeByCompletionKey.putAll(byKey);
        }

        if (duplicates.isEmpty()) {
            return CompletableFuture.completedFuture(activeOperations());
        }
        List<CompletableFuture<Void>> moves = new ArrayList<>();
        for (LoadedOperation duplicate : duplicates) {
            String error = "Duplicate active completion key during journal load: "
                    + duplicate.operation().completionKey();
            logger.warning(error + " operation=" + duplicate.operation().operationId());
            CookingCompletionOperation quarantined = duplicate.operation()
                    .withError(error)
                    .withStatus(Status.QUARANTINED);
            moves.add(moveLoadedToQuarantine(duplicate.path(), quarantined));
        }
        return CompletableFuture.allOf(moves.toArray(CompletableFuture[]::new))
                .thenApply(_ -> activeOperations());
    }

    private LoadedOperation loadOne(Path path) {
        try {
            YamlSection root = YamlFiles.load(path.toFile());
            return new LoadedOperation(decode(root.asMap()), path);
        } catch (Throwable throwable) {
            quarantineCorruptFile(path, throwable);
            return null;
        }
    }

    private List<Path> listActiveFiles() {
        try {
            Files.createDirectories(activeDirectory);
            try (Stream<Path> stream = Files.list(activeDirectory)) {
                return stream
                        .filter(Files::isRegularFile)
                        .filter(this::isYamlFile)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            }
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private void quarantineCorruptFile(Path source, Throwable throwable) {
        String fileName = source.getFileName() == null ? "unknown.yml" : source.getFileName().toString();
        String stem = fileName.replaceFirst("(?i)\\.ya?ml$", "");
        Path target = quarantineDirectory.resolve(sanitize(stem) + "-corrupt-" + System.currentTimeMillis() + ".yml");
        try {
            Files.createDirectories(quarantineDirectory);
            moveReplacing(source, target);
            logger.warning("Quarantined corrupt cooking completion journal '" + fileName + "': "
                    + rootCauseMessage(throwable));
        } catch (IOException moveFailure) {
            logger.warning("Failed to load cooking completion journal '" + fileName + "': "
                    + rootCauseMessage(throwable) + "; quarantine move failed: " + rootCauseMessage(moveFailure));
        }
    }

    private CompletableFuture<Void> moveLoadedToQuarantine(
            Path source,
            CookingCompletionOperation operation) {
        return writeIo(source, "cooking-completion-quarantine-duplicate", () -> {
            saveAtomic(quarantinePath(operation.operationId()), operation);
            deleteIfExists(source);
        });
    }

    private CompletableFuture<CookingCompletionOperation> moveFromActive(
            CookingCompletionOperation operation,
            Path destination,
            String taskName) {
        Path source = activePath(operation.operationId());
        CompletableFuture<CookingCompletionOperation> moved = writeIo(source, taskName, () -> {
            saveAtomic(destination, operation);
            deleteIfExists(source);
        }).thenApply(_ -> operation);
        return moved.whenComplete((_, throwable) -> {
            if (throwable == null) {
                removeFromIndex(operation.operationId());
            }
        });
    }

    private CompletableFuture<Void> writeOperation(
            Path path,
            CookingCompletionOperation operation,
            String taskName) {
        return writeIo(path, taskName + ":" + operation.operationId(), () -> saveAtomic(path, operation));
    }

    private void saveAtomic(Path path, CookingCompletionOperation operation) {
        try {
            YamlFiles.save(path.toFile(), encode(operation));
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private <T> CompletableFuture<T> readIo(Path path, String taskName, Supplier<T> action) {
        if (fileScope != null) {
            return fileScope.read(path, taskName, action);
        }
        return synchronous(path, action);
    }

    private CompletableFuture<Void> writeIo(Path path, String taskName, Runnable action) {
        if (fileScope != null) {
            return fileScope.write(path, taskName, action);
        }
        return synchronous(path, () -> {
            action.run();
            return null;
        });
    }

    private <T> CompletableFuture<T> synchronous(Path path, Supplier<T> action) {
        Object lock = synchronousPathLocks.computeIfAbsent(path.toAbsolutePath().normalize(), _ -> new Object());
        synchronized (lock) {
            try {
                return CompletableFuture.completedFuture(action.get());
            } catch (Throwable throwable) {
                return CompletableFuture.failedFuture(throwable);
            }
        }
    }

    private void indexNonTerminal(CookingCompletionOperation operation) {
        if (!operation.isTerminal()) {
            activeByCompletionKey.put(operation.completionKey(), operation);
        }
    }

    private void removeFromIndex(CookingCompletionOperation operation) {
        synchronized (indexLock) {
            activeById.remove(operation.operationId(), operation);
            removeCompletionKeyIndex(operation);
        }
    }

    private void removeFromIndex(String operationId) {
        synchronized (indexLock) {
            CookingCompletionOperation removed = activeById.remove(operationId);
            if (removed != null) {
                removeCompletionKeyIndex(removed);
            }
        }
    }

    private void removeCompletionKeyIndex(CookingCompletionOperation operation) {
        activeByCompletionKey.remove(operation.completionKey(), operation);
    }

    private void restoreIndex(
            CookingCompletionOperation attempted,
            CookingCompletionOperation previous) {
        synchronized (indexLock) {
            activeById.remove(attempted.operationId(), attempted);
            removeCompletionKeyIndex(attempted);
            if (previous != null) {
                activeById.put(previous.operationId(), previous);
                indexNonTerminal(previous);
            }
        }
    }

    private Path activePath(String operationId) {
        return activeDirectory.resolve(sanitize(operationId) + ".yml");
    }

    private Path archivePath(String operationId) {
        return archiveDirectory.resolve(sanitize(operationId) + ".yml");
    }

    private Path quarantinePath(String operationId) {
        return quarantineDirectory.resolve(sanitize(operationId) + ".yml");
    }

    private String sanitize(String value) {
        String normalized = value == null ? "operation" : value.replaceAll("[^a-zA-Z0-9._-]+", "_");
        return normalized.isBlank() ? "operation" : normalized;
    }

    private boolean isYamlFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private <T> CompletableFuture<T> rejectedFuture() {
        return CompletableFuture.failedFuture(new RejectedExecutionException(
                "Cooking completion journal is sealed"));
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = AsyncFailures.unwrap(throwable);
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank()
                ? current == null ? "unknown error" : current.getClass().getSimpleName()
                : message;
    }

    private Map<String, Object> encode(CookingCompletionOperation operation) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schema_version", SCHEMA_VERSION);
        root.put("operation_id", operation.operationId());
        root.put("completion_key", operation.completionKey());
        root.put("status", operation.status().name());
        root.put("station_type", operation.stationType().name());
        root.put("station_coordinates", encodeCoordinates(operation.stationCoordinates()));
        root.put("expected_state", operation.expectedState());
        root.put("expected_state_digest", operation.expectedStateDigest());
        root.put("commit_mode", operation.commitMode().name());
        root.put("committed_state", operation.committedState());
        root.put("committed_state_digest", operation.committedStateDigest());
        root.put("input_units", encodeUnits(operation.inputUnits()));
        root.put("delivery_units", encodeUnits(operation.deliveryUnits()));
        root.put("created_at_ms", operation.createdAtMs());
        root.put("updated_at_ms", operation.updatedAtMs());
        root.put("last_error", operation.lastError());
        return root;
    }

    private Map<String, Object> encodeCoordinates(StationCoordinates coordinates) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("world", coordinates.world());
        values.put("x", coordinates.x());
        values.put("y", coordinates.y());
        values.put("z", coordinates.z());
        return values;
    }

    private List<Map<String, Object>> encodeUnits(List<Unit> units) {
        List<Map<String, Object>> values = new ArrayList<>(units.size());
        for (Unit unit : units) {
            Map<String, Object> serialized = new LinkedHashMap<>();
            serialized.put("unit_id", unit.unitId());
            serialized.put("kind", unit.kind().name());
            serialized.put("state", unit.state().name());
            serialized.put("semantics", unit.semantics().name());
            serialized.put("payload", unit.payload());
            serialized.put("attempts", unit.attempts());
            serialized.put("last_error", unit.lastError());
            values.add(serialized);
        }
        return values;
    }

    private CookingCompletionOperation decode(Map<String, Object> root) {
        int schemaVersion = intValue(root.get("schema_version"), -1);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported completion journal schema: " + schemaVersion);
        }
        Map<String, Object> coordinates = mapValue(root.get("station_coordinates"));
        StationCoordinates stationCoordinates = new StationCoordinates(
                requiredString(coordinates, "world"),
                intValue(coordinates.get("x"), Integer.MIN_VALUE),
                intValue(coordinates.get("y"), Integer.MIN_VALUE),
                intValue(coordinates.get("z"), Integer.MIN_VALUE)
        );
        if (stationCoordinates.x() == Integer.MIN_VALUE
                || stationCoordinates.y() == Integer.MIN_VALUE
                || stationCoordinates.z() == Integer.MIN_VALUE) {
            throw new IllegalArgumentException("Invalid station coordinates");
        }
        return new CookingCompletionOperation(
                requiredString(root, "operation_id"),
                requiredString(root, "completion_key"),
                ConfigNodes.enumOrThrow(Status.class, root.get("status")),
                ConfigNodes.enumOrThrow(StationType.class, root.get("station_type")),
                stationCoordinates,
                mapValue(root.get("expected_state")),
                requiredString(root, "expected_state_digest"),
                ConfigNodes.enumOrThrow(CommitMode.class, root.get("commit_mode")),
                mapValue(root.get("committed_state")),
                requiredString(root, "committed_state_digest"),
                decodeUnits(root.get("input_units")),
                decodeUnits(root.get("delivery_units")),
                longValue(root.get("created_at_ms"), -1L),
                longValue(root.get("updated_at_ms"), -1L),
                stringValue(root.get("last_error"))
        );
    }

    private List<Unit> decodeUnits(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("Unit collection must be a list");
        }
        List<Unit> units = new ArrayList<>(list.size());
        for (Object entry : list) {
            Map<String, Object> serialized = mapValue(entry);
            units.add(new Unit(
                    requiredString(serialized, "unit_id"),
                    ConfigNodes.enumOrThrow(UnitKind.class, serialized.get("kind")),
                    ConfigNodes.enumOrThrow(UnitState.class, serialized.get("state")),
                    ConfigNodes.enumOrThrow(Semantics.class, serialized.get("semantics")),
                    mapValue(serialized.get("payload")),
                    intValue(serialized.get("attempts"), 0),
                    stringValue(serialized.get("last_error"))
            ));
        }
        return List.copyOf(units);
    }

    private Map<String, Object> mapValue(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof YamlSection section) {
            return section.asMap();
        }
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected a map, got " + value.getClass().getSimpleName());
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return normalized;
    }

    private String requiredString(Map<String, Object> values, String key) {
        String value = stringValue(values.get(key)).trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Missing journal value: " + key);
        }
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (NumberFormatException _) {
            return fallback;
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(stringValue(value));
        } catch (NumberFormatException _) {
            return fallback;
        }
    }

    private record LoadedOperation(CookingCompletionOperation operation, Path path) {
    }
}
