package emaki.jiuwu.craft.storage.log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;

public final class StorageOperationLog {

    public record DrainReport(int entriesWritten, boolean drained, int pendingEntries) {
    }

    private final Path logRoot;
    private final Logger logger;
    private final AsyncFileService.FileScope fileScope;
    private final Map<UUID, List<StorageLogEntry>> pending = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicBoolean> flushScheduled = new ConcurrentHashMap<>();
    private final AtomicBoolean sealed = new AtomicBoolean();
    private final AtomicInteger entriesWritten = new AtomicInteger();

    private volatile AppConfig.LoggingConfig config;
    private volatile CompletableFuture<DrainReport> drainFuture;

    public StorageOperationLog(Path logRoot,
            Logger logger,
            AsyncFileService.FileScope fileScope,
            AppConfig.LoggingConfig config) {
        this.logRoot = logRoot;
        this.logger = logger;
        this.fileScope = fileScope;
        this.config = config == null ? AppConfig.LoggingConfig.defaults() : config;
    }

    public void reconfigure(AppConfig.LoggingConfig config) {
        this.config = config == null ? AppConfig.LoggingConfig.defaults() : config;
    }

    public void record(StorageLogEntry entry) {
        if (entry == null) {
            return;
        }
        if (!shouldRecord(entry)) {
            return;
        }
        if (sealed.get()) {
            LogTags.send(logger, LogTag.JOURNAL, Level.WARNING, "journal.entry_after_shutdown_flush_discarded", Map.of());
            return;
        }
        List<StorageLogEntry> queue = pending.computeIfAbsent(entry.playerId(), id -> new ArrayList<>());
        synchronized (queue) {
            queue.add(entry);
        }
        scheduleFlush(entry.playerId());
    }

    private boolean shouldRecord(StorageLogEntry entry) {
        if (entry.type().forced()) {
            return true;
        }
        AppConfig.LoggingConfig active = config;
        if (!active.enabled()) {
            return false;
        }
        List<String> sources = active.sources();
        return sources.isEmpty() || sources.contains(entry.source().id());
    }

    private void scheduleFlush(UUID playerId) {
        AtomicBoolean gate = flushScheduled.computeIfAbsent(playerId, id -> new AtomicBoolean());
        if (!gate.compareAndSet(false, true)) {
            return;
        }
        Path playerDirectory = logRoot.resolve(playerId.toString());
        fileScope.write(playerDirectory, "storage-log-append", () -> {
            gate.set(false);
            try {
                flushPlayer(playerId);
            } finally {
                flushScheduled.remove(playerId, gate);
            }
        }).exceptionally(throwable -> {
            gate.set(false);
            flushScheduled.remove(playerId, gate);
            LogTags.send(logger, LogTag.JOURNAL, Level.WARNING, "journal.append_log_failed", Map.of("player", playerId, "error", describeFailure(throwable)));
            return null;
        });
    }

    private void flushPlayer(UUID playerId) {
        List<StorageLogEntry> batch = pending.remove(playerId);
        if (batch == null || batch.isEmpty()) {
            return;
        }
        Map<String, StringBuilder> byFile = new ConcurrentHashMap<>();
        for (StorageLogEntry entry : batch) {
            byFile.computeIfAbsent(entry.fileName(), name -> new StringBuilder())
                    .append(entry.render())
                    .append(System.lineSeparator());
        }
        Path directory = logRoot.resolve(playerId.toString());
        try {
            Files.createDirectories(directory);
            for (Map.Entry<String, StringBuilder> file : byFile.entrySet()) {
                Files.writeString(directory.resolve(file.getKey()),
                        file.getValue().toString(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            }
            entriesWritten.addAndGet(batch.size());
        } catch (IOException failure) {
            requeue(playerId, batch);
            LogTags.send(logger, LogTag.JOURNAL, Level.WARNING, "journal.write_log_failed_requeued", Map.of("player", playerId, "count", batch.size(), "error", describeFailure(failure)));
        }
    }

    private void requeue(UUID playerId, List<StorageLogEntry> batch) {
        List<StorageLogEntry> queue = pending.computeIfAbsent(playerId, id -> new ArrayList<>());
        synchronized (queue) {
            queue.addAll(0, batch);
        }
    }

    public synchronized CompletableFuture<DrainReport> flushAll() {
        if (!sealed.compareAndSet(false, true)) {
            return drainFuture == null ? CompletableFuture.completedFuture(null) : drainFuture;
        }
        entriesWritten.set(0);
        for (UUID playerId : Set.copyOf(pending.keySet())) {
            flushPlayer(playerId);
        }
        CompletableFuture<DrainReport> drain = fileScope.waitForIdle()
                .handle((ignored, throwable) -> {
                    int unsaved = pendingEntries();
                    if (unsaved > 0) {
                        for (UUID playerId : Set.copyOf(pending.keySet())) {
                            flushPlayer(playerId);
                        }
                        unsaved = pendingEntries();
                    }
                    if (unsaved > 0) {
                        LogTags.send(logger, LogTag.JOURNAL, Level.WARNING, "journal.unflushed_entries_at_shutdown", Map.of("count", unsaved, "error", (throwable == null ? "" : ": " + describeFailure(throwable))));
                    }
                    return new DrainReport(entriesWritten.get(), throwable == null && unsaved == 0, unsaved);
                });
        drainFuture = drain;
        return drain;
    }

    private int pendingEntries() {
        int total = 0;
        for (List<StorageLogEntry> queue : pending.values()) {
            synchronized (queue) {
                total += queue.size();
            }
        }
        return total;
    }

    private String describeFailure(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null && cause.getCause() != null && cause != cause.getCause()
                && cause instanceof CompletionException) {
            cause = cause.getCause();
        }
        if (cause == null) {
            return "未知错误";
        }
        return cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : " " + cause.getMessage());
    }

    public int purgeExpired() {
        AppConfig.LoggingConfig active = config;
        int retentionDays = active.retentionDays();
        if (retentionDays <= 0 || !Files.isDirectory(logRoot)) {
            return 0;
        }
        LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
        int deleted = 0;
        try (Stream<Path> directories = Files.list(logRoot)) {
            for (Path directory : directories.filter(Files::isDirectory).toList()) {
                deleted += purgeDirectory(directory, cutoff);
            }
        } catch (IOException failure) {
            LogTags.send(logger, LogTag.JOURNAL, Level.WARNING, "journal.scan_log_directory_failed", Map.of("error", describeFailure(failure)));
        }
        return deleted;
    }

    private int purgeDirectory(Path directory, LocalDate cutoff) {
        int deleted = 0;
        boolean remaining = false;
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.toList()) {
                LocalDate stamp = parseDate(file.getFileName().toString());
                if (stamp != null && stamp.isBefore(cutoff)) {
                    if (Files.deleteIfExists(file)) {
                        deleted++;
                    }
                } else {
                    remaining = true;
                }
            }
        } catch (IOException failure) {
            LogTags.send(logger, LogTag.JOURNAL, Level.WARNING, "journal.cleanup_log_directory_failed", Map.of("directory", directory.getFileName(), "error", describeFailure(failure)));
            return deleted;
        }
        if (!remaining) {
            try {
                Files.deleteIfExists(directory);
            } catch (IOException failure) {
                LogTags.send(logger, LogTag.JOURNAL, Level.WARNING, "journal.delete_empty_log_directory_failed", Map.of("directory", directory.getFileName(), "error", describeFailure(failure)));
            }
        }
        return deleted;
    }

    private LocalDate parseDate(String fileName) {
        if (!fileName.endsWith(".log")) {
            return null;
        }
        try {
            return LocalDate.parse(fileName.substring(0, fileName.length() - 4));
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

}
