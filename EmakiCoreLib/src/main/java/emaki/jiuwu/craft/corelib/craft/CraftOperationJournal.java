package emaki.jiuwu.craft.corelib.craft;

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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.FileScope;
import emaki.jiuwu.craft.corelib.async.AsyncTaskScheduler;
import emaki.jiuwu.craft.corelib.yaml.AsyncYamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public final class CraftOperationJournal<R> {

    public interface Codec<R> {
        Map<String, Object> encode(R payload);

        R decode(YamlSection section);
    }

    public record Entry<R>(String operationId, String kind, UUID playerId, String phase, R payload) {}

    private final Object lock = new Object();
    private final LinkedHashMap<String, Entry<R>> entries = new LinkedHashMap<>();
    private int inFlight;
    private final int maxEntries;

    private final Codec<R> codec;
    private final JavaPlugin ownerPlugin;
    private final Path activeDirectory;
    private final Path completedDirectory;
    private final Path quarantineDirectory;
    private volatile AsyncYamlFiles asyncYamlFiles;
    private volatile FileScope fileScope;
    private volatile boolean asyncFilesUnavailableLogged;
    private volatile boolean asyncSchedulerUnavailableLogged;
    private final List<CompletableFuture<Void>> quiesceWaiters = new ArrayList<>();

    private CraftOperationJournal(int maxEntries, Codec<R> codec, JavaPlugin plugin, Path root) {
        this.maxEntries = Math.max(1, maxEntries);
        this.codec = codec;
        this.ownerPlugin = plugin;
        if (root != null) {
            this.activeDirectory = root.resolve("active");
            this.completedDirectory = root.resolve("completed");
            this.quarantineDirectory = root.resolve("quarantine");
        } else {
            this.activeDirectory = null;
            this.completedDirectory = null;
            this.quarantineDirectory = null;
        }
    }

    public static <R> CraftOperationJournal<R> ofMemory(int maxEntries) {
        return new CraftOperationJournal<>(maxEntries, null, null, null);
    }

    public static <R> CraftOperationJournal<R> ofPersisted(int maxEntries, Codec<R> codec,
            JavaPlugin plugin, Path journalRoot) {
        return new CraftOperationJournal<>(maxEntries, codec, plugin, journalRoot);
    }

    public String begin(String kind, UUID playerId, R initialPayload) {
        return begin(null, kind, playerId, initialPayload);
    }

    public String begin(String operationId, String kind, UUID playerId, R initialPayload) {
        return begin(operationId, kind, playerId, "IN_FLIGHT", initialPayload);
    }

    public String begin(String operationId, String kind, UUID playerId, String initialPhase, R initialPayload) {
        String resolvedId = (operationId == null || operationId.isBlank())
                ? UUID.randomUUID().toString() : operationId;
        Entry<R> entry = new Entry<>(resolvedId, kind, playerId,
                initialPhase == null ? "IN_FLIGHT" : initialPhase, initialPayload);
        synchronized (lock) {
            entries.put(resolvedId, entry);
            inFlight++;
        }
        persistAsync(entry);
        return resolvedId;
    }

    public void restore(String operationId, String kind, UUID playerId, String phase, R payload) {
        if (operationId == null || operationId.isBlank()) {
            return;
        }
        Entry<R> entry = new Entry<>(operationId, kind, playerId,
                phase == null ? "IN_FLIGHT" : phase, payload);
        synchronized (lock) {
            entries.put(operationId, entry);
            inFlight++;
        }
    }

    public Entry<R> beginIfAbsent(String operationId, String kind, UUID playerId, R payload) {
        if (operationId == null || operationId.isBlank()) {
            return null;
        }
        Entry<R> entry = new Entry<>(operationId, kind, playerId, "IN_FLIGHT", payload);
        synchronized (lock) {
            Entry<R> existing = entries.get(operationId);
            if (existing != null) {
                return existing;
            }
            entries.put(operationId, entry);
            inFlight++;
        }
        persistAsync(entry);
        return null;
    }

    public void update(String operationId, String phase, R payload) {
        if (operationId == null) {
            return;
        }
        Entry<R> updated;
        synchronized (lock) {
            Entry<R> current = entries.get(operationId);
            if (current == null) {
                return;
            }
            updated = new Entry<>(operationId, current.kind(), current.playerId(), phase, payload);
            entries.put(operationId, updated);
        }
        persistAsync(updated);
    }

    public void release(String operationId) {
        synchronized (lock) {
            inFlight = Math.max(0, inFlight - 1);
            lock.notifyAll();
            completeQuiesceWaitersIfDrained();
        }
    }

    public CompletableFuture<Void> archive(String operationId) {
        if (operationId == null) {
            return CompletableFuture.completedFuture(null);
        }
        Entry<R> entry;
        synchronized (lock) {
            entry = entries.remove(operationId);
            inFlight = Math.max(0, inFlight - 1);
            lock.notifyAll();
            completeQuiesceWaitersIfDrained();
        }
        if (entry == null || activeDirectory == null) {
            return CompletableFuture.completedFuture(null);
        }
        return archiveFile(entry);
    }

    public Entry<R> get(String operationId) {
        if (operationId == null) return null;
        synchronized (lock) { return entries.get(operationId); }
    }

    public boolean contains(String operationId) {
        if (operationId == null) return false;
        synchronized (lock) { return entries.containsKey(operationId); }
    }

    public int inFlightCount() {
        synchronized (lock) { return inFlight; }
    }

    public Map<String, Entry<R>> snapshot() {
        synchronized (lock) { return Map.copyOf(entries); }
    }

    public void prune(Predicate<Entry<R>> canRemove) {
        synchronized (lock) {
            if (entries.size() <= maxEntries) return;
            var iter = entries.entrySet().iterator();
            while (entries.size() > maxEntries && iter.hasNext()) {
                if (canRemove.test(iter.next().getValue())) {
                    iter.remove();
                }
            }
        }
    }

    public boolean drain(long timeout, TimeUnit unit) {
        long nanos = Math.max(0L, unit == null ? 0L : unit.toNanos(timeout));
        long deadline = System.nanoTime() + nanos;
        synchronized (lock) {
            while (inFlight > 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) return false;
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    public CompletableFuture<Void> quiesce() {
        synchronized (lock) {
            if (inFlight == 0) {
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> future = new CompletableFuture<>();
            quiesceWaiters.add(future);
            return future;
        }
    }

    public CompletableFuture<List<Entry<R>>> loadActive() {
        if (activeDirectory == null || codec == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        Supplier<List<Entry<R>>> load = () -> {
            List<Path> files = listActiveFiles();
            List<Entry<R>> result = new ArrayList<>(files.size());
            for (Path file : files) {
                Entry<R> entry = loadOne(file);
                if (entry != null) {
                    result.add(entry);
                }
            }
            return List.copyOf(result);
        };
        AsyncTaskScheduler scheduler = resolveAsyncTaskScheduler();
        return scheduler == null
                ? CompletableFuture.supplyAsync(load)
                : scheduler.supplyAsync("craft-journal-load-active", 0L, load);
    }

    private List<Path> listActiveFiles() {
        try {
            if (!Files.isDirectory(activeDirectory)) {
                return List.of();
            }
            List<Path> files = new ArrayList<>();
            try (var stream = Files.list(activeDirectory)) {
                stream.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                      .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                      .forEach(files::add);
            }
            return files;
        } catch (IOException exception) {
            warn("Failed to list active journal directory: " + rootCauseMessage(exception));
            return List.of();
        }
    }

    private Entry<R> loadOne(Path file) {
        try {
            AsyncYamlFiles files = resolveAsyncFiles();
            if (files == null) {
                return null;
            }
            YamlSection section = files.load(file.toFile()).join();
            if (section == null) {
                return null;
            }
            String operationId = section.getString("operation_id", "");
            String kind = section.getString("kind", "");
            String playerIdStr = section.getString("player_id", "");
            String phase = section.getString("phase", "IN_FLIGHT");
            UUID playerId = null;
            if (!playerIdStr.isEmpty()) {
                try {
                    playerId = UUID.fromString(playerIdStr);
                } catch (IllegalArgumentException ignored) {
                }
            }
            return new Entry<>(operationId, kind, playerId, phase, codec.decode(section));
        } catch (Exception exception) {
            quarantineCorruptFile(file, exception);
            return null;
        }
    }

    private void quarantineCorruptFile(Path file, Exception cause) {
        warn("Corrupt journal file " + file.getFileName() + ": " + rootCauseMessage(cause));
        if (quarantineDirectory == null) {
            return;
        }
        try {
            Files.createDirectories(quarantineDirectory);
            moveReplacing(file, quarantineDirectory.resolve(file.getFileName()));
        } catch (IOException moveException) {
            warn("Failed to quarantine " + file.getFileName() + ": " + rootCauseMessage(moveException));
        }
    }

    private void persistAsync(Entry<R> entry) {
        if (codec == null || activeDirectory == null) {
            return;
        }
        AsyncYamlFiles files = resolveAsyncFiles();
        if (files == null) {
            return;
        }
        try {
            Files.createDirectories(activeDirectory);
        } catch (IOException e) {
            warn("Failed to create journal directory: " + rootCauseMessage(e));
            return;
        }
        files.save(activePath(entry.operationId()).toFile(), encodeEntry(entry))
                .exceptionally(error -> {
                    warn("Failed to write journal for operation " + entry.operationId()
                            + ": " + rootCauseMessage(error));
                    return null;
                });
    }

    private CompletableFuture<Void> archiveFile(Entry<R> entry) {
        Path source = activePath(entry.operationId());
        if (!Files.isRegularFile(source)) {
            return CompletableFuture.completedFuture(null);
        }
        FileScope scope = resolveFileScope();
        if (scope == null) {
            warn("Failed to archive operation " + entry.operationId()
                    + ": async file service unavailable");
            return CompletableFuture.completedFuture(null);
        }
        Path target = completedDirectory.resolve(entry.operationId() + ".yml");
        return scope.write(source, "craft-journal-archive:" + entry.operationId(), () -> {
            try {
                Files.createDirectories(completedDirectory);
                moveReplacing(source, target);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).handle((_, throwable) -> {
            if (throwable != null) {
                warn("Failed to archive operation " + entry.operationId()
                        + ": " + rootCauseMessage(throwable));
            }
            return null;
        });
    }

    private Map<String, Object> encodeEntry(Entry<R> entry) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("operation_id", entry.operationId());
        data.put("kind", entry.kind());
        data.put("player_id", entry.playerId() == null ? "" : entry.playerId().toString());
        data.put("phase", entry.phase());
        if (codec != null && entry.payload() != null) {
            data.putAll(codec.encode(entry.payload()));
        }
        return data;
    }

    private void completeQuiesceWaitersIfDrained() {
        if (inFlight == 0 && !quiesceWaiters.isEmpty()) {
            List<CompletableFuture<Void>> waiters = new ArrayList<>(quiesceWaiters);
            quiesceWaiters.clear();
            for (CompletableFuture<Void> waiter : waiters) {
                waiter.complete(null);
            }
        }
    }

    private Path activePath(String operationId) {
        return activeDirectory.resolve(operationId + ".yml");
    }

    private AsyncYamlFiles resolveAsyncFiles() {
        if (asyncYamlFiles != null) {
            return asyncYamlFiles;
        }
        synchronized (this) {
            if (asyncYamlFiles != null) {
                return asyncYamlFiles;
            }
            try {
                EmakiCoreLibPlugin coreLib = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
                this.fileScope = coreLib.asyncFileScope(ownerPlugin);
                this.asyncYamlFiles = new AsyncYamlFiles(fileScope);
            } catch (RuntimeException | LinkageError failure) {
                if (!asyncFilesUnavailableLogged) {
                    asyncFilesUnavailableLogged = true;
                    warn("Cannot reach async file service: " + rootCauseMessage(failure));
                }
            }
            return asyncYamlFiles;
        }
    }

    private FileScope resolveFileScope() {
        resolveAsyncFiles();
        return fileScope;
    }

    private AsyncTaskScheduler resolveAsyncTaskScheduler() {
        try {
            return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class).asyncTaskScheduler();
        } catch (RuntimeException | LinkageError failure) {
            if (!asyncSchedulerUnavailableLogged) {
                asyncSchedulerUnavailableLogged = true;
                warn("Cannot reach async task scheduler, falling back to the common pool: "
                        + rootCauseMessage(failure));
            }
            return null;
        }
    }

    private void warn(String message) {
        if (ownerPlugin != null) {
            ownerPlugin.getLogger().warning("[CraftOperationJournal] " + message);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        if (current == null) return "unknown error";
        String message = current.getMessage();
        return (message == null || message.isBlank()) ? current.getClass().getSimpleName() : message;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
