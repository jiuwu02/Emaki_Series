package emaki.jiuwu.craft.dungeon.audit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.dungeon.config.AppConfig;

public final class AuditService implements AutoCloseable {

    private static final String FILE_PREFIX = "audit-";
    private static final String FILE_SUFFIX = ".log";
    private static final DateTimeFormatter FILE_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final File logsDirectory;
    private final Supplier<AppConfig> config;
    private final MessageService messages;

    private final Object lock = new Object();
    private LocalDate currentDay;
    private BufferedWriter writer;
    private String currentFile = "";
    private boolean writeFailed;

    public AuditService(File dataFolder, Supplier<AppConfig> config, MessageService messages) {
        this.logsDirectory = new File(dataFolder, "logs");
        this.config = config;
        this.messages = messages;
    }

    public void write(String category, Map<String, String> fields) {
        if (category == null || category.isBlank()) {
            return;
        }
        StringBuilder line = new StringBuilder(96);
        line.append(STAMP.format(LocalDateTime.now()))
                .append(" [").append(category).append(']');
        if (fields != null) {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                line.append(' ').append(entry.getKey()).append('=')
                        .append(sanitize(entry.getValue()));
            }
        }
        synchronized (lock) {
            try {
                ensureWriter(LocalDate.now());
                if (writer == null) {
                    return;
                }
                writer.write(line.toString());
                writer.newLine();
                writer.flush();
                writeFailed = false;
            } catch (IOException exception) {
                if (!writeFailed) {
                    writeFailed = true;
                    messages.warning("console.audit_write_failed", Map.of(
                            "file", currentFile,
                            "error", String.valueOf(exception.getMessage())));
                }
            }
        }
    }

    private void ensureWriter(LocalDate today) throws IOException {
        if (writer != null && today.equals(currentDay)) {
            return;
        }
        closeWriter();
        if (!logsDirectory.isDirectory() && !logsDirectory.mkdirs()) {
            throw new IOException("无法创建审计目录 " + logsDirectory);
        }
        currentDay = today;
        currentFile = FILE_PREFIX + today.format(FILE_DAY) + FILE_SUFFIX;
        Path target = new File(logsDirectory, currentFile).toPath();
        writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        sweepExpired(today);
    }

    private void sweepExpired(LocalDate today) {
        int retention = Math.max(1, config.get().debugRetentionDays());
        LocalDate limit = today.minusDays(retention);
        try (Stream<Path> files = Files.list(logsDirectory.toPath())) {
            files.filter(path -> path.getFileName().toString().startsWith(FILE_PREFIX))
                    .filter(path -> path.getFileName().toString().endsWith(FILE_SUFFIX))
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        String raw = name.substring(FILE_PREFIX.length(),
                                name.length() - FILE_SUFFIX.length());
                        try {
                            return LocalDate.parse(raw, FILE_DAY).isBefore(limit);
                        } catch (RuntimeException exception) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private void closeWriter() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException ignored) {
        }
        writer = null;
    }

    private static String sanitize(String value) {
        return value.replace('\n', ' ').replace('\r', ' ').replace('|', '/');
    }

    @Override
    public void close() {
        synchronized (lock) {
            closeWriter();
            currentDay = null;
        }
    }
}
