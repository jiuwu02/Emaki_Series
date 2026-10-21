package emaki.jiuwu.craft.dummy.log;

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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

public final class DummyLogger implements AutoCloseable {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int RECENT_LINE_LIMIT = 4096;

    private final Path logsRoot;
    private final int retentionDays;
    private final boolean perDummyFiles;

    private final Object lock = new Object();
    private final Deque<String> recentLines = new ArrayDeque<>();
    private BufferedWriter writer;
    private LocalDate currentDay;
    private String currentFile = "";

    public DummyLogger(Path logsRoot, int retentionDays, boolean perDummyFiles) {
        this.logsRoot = logsRoot;
        this.retentionDays = retentionDays;
        this.perDummyFiles = perDummyFiles;
    }

    public int open() {
        synchronized (lock) {
            int removed = sweepExpired();
            ensureWriter(LocalDate.now());
            return removed;
        }
    }

    public void log(LogLevel level, String actor, String message) {
        String stamp = STAMP.format(LocalDateTime.now());
        String safeActor = actor == null || actor.isBlank() ? "system" : actor;
        String safeMessage = message == null ? "" : message;
        String line = stamp + " [" + level.token() + "] [" + safeActor + "] " + safeMessage;
        synchronized (lock) {
            ensureWriter(LocalDate.now());
            writeLine(line);
            if (perDummyFiles && !safeActor.equals("system")) {
                writeDummyLine(safeActor, line);
            }
            recentLines.addLast(line);
            while (recentLines.size() > RECENT_LINE_LIMIT) {
                recentLines.removeFirst();
            }
        }
    }

    public boolean recentContains(String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        synchronized (lock) {
            return recentLines.stream().anyMatch(line -> line.contains(needle));
        }
    }

    public boolean recentMatch(String levelToken, String actor, String needle) {
        if (needle == null || needle.isBlank() || actor == null || actor.isBlank()) {
            return false;
        }
        String marker = "[" + levelToken + "] [" + actor + "] ";
        synchronized (lock) {
            return recentLines.stream().anyMatch(
                    line -> line.contains(marker) && line.contains(needle));
        }
    }

    public void clearRecent() {
        synchronized (lock) {
            recentLines.clear();
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            closeWriter();
        }
    }

    private void writeLine(String line) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException exception) {
            closeWriter();
        }
    }

    private void writeDummyLine(String actor, String line) {
        Path target = logsRoot.resolve(actor + ".log");
        try (BufferedWriter dummyWriter = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            dummyWriter.write(line);
            dummyWriter.newLine();
        } catch (IOException exception) {
            writeLine("[" + LogLevel.ERROR.token() + "] [system] dummy_log_write_failed file="
                    + actor + ".log error=" + exception.getMessage());
        }
    }

    private void ensureWriter(LocalDate today) {
        if (writer != null && today.equals(currentDay)) {
            return;
        }
        closeWriter();
        try {
            Files.createDirectories(logsRoot);
            currentDay = today;
            currentFile = today.format(FILE_DAY) + ".log";
            Path target = logsRoot.resolve(currentFile);
            writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            writer = null;
        }
    }

    private void closeWriter() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException exception) {
            writer = null;
            return;
        }
        writer = null;
    }

    private int sweepExpired() {
        if (retentionDays <= 0) {
            return 0;
        }
        if (!Files.isDirectory(logsRoot)) {
            return 0;
        }
        LocalDate limit = LocalDate.now().minusDays(retentionDays);
        int removed = 0;
        try (Stream<Path> files = Files.list(logsRoot)) {
            List<Path> expired = new ArrayList<>();
            for (Path path : files.toList()) {
                String name = path.getFileName().toString();
                if (!name.endsWith(".log")) {
                    continue;
                }
                String day = name.substring(0, name.length() - 4);
                if (day.length() < 10) {
                    continue;
                }
                try {
                    if (LocalDate.parse(day, FILE_DAY).isBefore(limit)) {
                        expired.add(path);
                    }
                } catch (RuntimeException parseFailure) {
                    continue;
                }
            }
            for (Path path : expired) {
                if (Files.deleteIfExists(path)) {
                    removed++;
                }
            }
        } catch (IOException exception) {
            return removed;
        }
        return removed;
    }

    public File logsDirectory() {
        return logsRoot.toFile();
    }
}
