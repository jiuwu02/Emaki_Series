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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import emaki.jiuwu.craft.corelib.async.AsyncFileService;
import emaki.jiuwu.craft.storage.config.AppConfig;

/**
 * Append-only flow log, one directory per player and one file per day.
 *
 * <p><strong>Write-only by design.</strong> Business logic never reads these files, which removes
 * the need for an index, compaction, schema versioning, corruption recovery or cross-file
 * transactions. A failed write never fails the storage transaction that produced it — it only
 * logs a warning.
 *
 * <p>Assembly and writing happen entirely off the owning thread: callers hand over an immutable
 * {@link StorageLogEntry} and return immediately. Records queued for the same player are flushed
 * as one append, so a 36-slot bulk deposit produces one file operation rather than 36.
 */
public final class StorageOperationLog {

    private final Path logRoot;
    private final Logger logger;
    private final AsyncFileService.FileScope fileScope;
    private final Map<UUID, List<StorageLogEntry>> pending = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicBoolean> flushScheduled = new ConcurrentHashMap<>();

    private volatile AppConfig.LoggingConfig config;

    /**
     * @param logRoot   {@code plugins/EmakiStorage/logs}
     * @param logger    the plugin logger, used only for warnings
     * @param fileScope the module's owner-scoped async file lane
     * @param config    the active logging settings
     */
    public StorageOperationLog(Path logRoot,
            Logger logger,
            AsyncFileService.FileScope fileScope,
            AppConfig.LoggingConfig config) {
        this.logRoot = logRoot;
        this.logger = logger;
        this.fileScope = fileScope;
        this.config = config == null ? AppConfig.LoggingConfig.defaults() : config;
    }

    /** Applies reloaded settings without dropping queued records. */
    public void reconfigure(AppConfig.LoggingConfig config) {
        this.config = config == null ? AppConfig.LoggingConfig.defaults() : config;
    }

    /**
     * Queues one record. Returns immediately; the caller never waits for IO.
     *
     * @param entry the record to write; ignored when filtered out by config
     */
    public void record(StorageLogEntry entry) {
        if (entry == null || !shouldRecord(entry)) {
            return;
        }
        pending.computeIfAbsent(entry.playerId(), id -> new ArrayList<>()).add(entry);
        scheduleFlush(entry.playerId());
    }

    /**
     * Decides whether a record survives the configured filters.
     *
     * <p>{@code ADMIN_*} records ignore both {@code enabled} and {@code sources}.
     */
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
            flushPlayer(playerId);
        }).exceptionally(throwable -> {
            gate.set(false);
            warn("Failed to append storage log for " + playerId, throwable);
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
        } catch (IOException failure) {
            warn("Failed to write storage log for " + playerId, failure);
        }
    }

    /**
     * Flushes every queued record and waits for the lane to settle.
     *
     * <p>Called during shutdown, before the file scope is drained.
     */
    public void flushAll() {
        for (UUID playerId : Set.copyOf(pending.keySet())) {
            flushPlayer(playerId);
        }
    }

    /**
     * Deletes expired daily files and removes player directories once they hold nothing.
     *
     * <p>Because files are split per day, expiry is a plain delete — no file is ever rewritten.
     *
     * @return how many files were deleted
     */
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
            warn("Failed to scan storage log directory", failure);
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
            warn("Failed to purge storage log directory " + directory.getFileName(), failure);
            return deleted;
        }
        if (!remaining) {
            try {
                Files.deleteIfExists(directory);
            } catch (IOException failure) {
                warn("Failed to remove empty storage log directory " + directory.getFileName(), failure);
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

    private void warn(String message, Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null && cause.getCause() != null && cause != cause.getCause()
                && cause instanceof java.util.concurrent.CompletionException) {
            cause = cause.getCause();
        }
        logger.log(Level.WARNING, message
                + (cause == null ? "" : ": " + cause.getClass().getSimpleName()
                        + (cause.getMessage() == null ? "" : " " + cause.getMessage())));
    }
}
