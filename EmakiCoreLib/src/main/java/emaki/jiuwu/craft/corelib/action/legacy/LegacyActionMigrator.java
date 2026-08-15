package emaki.jiuwu.craft.corelib.action.legacy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LegacyActionMigrator {

    private static final String MARKER = ".action-v2-migrated";

    private static final String BACKUP_SUFFIX = ".legacy-backup";

    private static final String FAILED_SUFFIX = ".v2-failed";

    private final LegacyFileScanner scanner = new LegacyFileScanner();
    private final PipelineChecker checker;

    public LegacyActionMigrator(@Nullable PipelineChecker checker) {
        this.checker = checker;
    }

    public @NotNull Report run(@Nullable Path markerDirectory,
            @NotNull List<Path> dataFolders,
            boolean dryRun) {
        if (markerDirectory != null && Files.exists(markerDirectory.resolve(MARKER))) {
            return Report.alreadyDone();
        }
        List<FileReport> files = new ArrayList<>();
        for (Path folder : dataFolders) {
            if (folder == null || !Files.isDirectory(folder)) {
                continue;
            }
            for (Path file : yamlFiles(folder)) {
                FileReport report = migrateFile(file, dryRun);
                if (report != null) {
                    files.add(report);
                }
            }
        }
        Report report = new Report(false, List.copyOf(files));

        if (!dryRun && markerDirectory != null
                && report.rejectedLines() == 0 && report.failedFiles() == 0) {
            writeMarker(markerDirectory, report);
        }
        return report;
    }

    private @Nullable FileReport migrateFile(Path file, boolean dryRun) {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | UncheckedIOException exception) {
            return new FileReport(file, 0, List.of(), List.of(),
                    "unreadable: " + exception.getMessage(), false);
        }
        LegacyFileScanner.Result scan = scanner.scan(content);
        List<String> skips = describeSkips(scan);
        if (!scan.hasChanges()) {
            return skips.isEmpty() ? null : new FileReport(file, 0, skips, List.of(), null, false);
        }

        Verification verified = verify(scan);
        if (verified.accepted().isEmpty()) {
            if (!dryRun) {
                writeQuietly(Path.of(file + FAILED_SUFFIX), scanner.rewrite(scan, scan.changes()));
            }
            return new FileReport(file, 0, skips, verified.rejections(),
                    "compile check rejected every converted line", false);
        }
        String rewritten = scanner.rewrite(scan, verified.accepted());
        if (dryRun) {
            return new FileReport(file, verified.accepted().size(), skips, verified.rejections(),
                    null, true);
        }
        try {

            Path backup = Path.of(file + BACKUP_SUFFIX);
            if (!Files.exists(backup)) {
                Files.copy(file, backup);
            }
            Path temporary = Path.of(file + ".v2-tmp");
            Files.writeString(temporary, rewritten, StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | UncheckedIOException exception) {
            return new FileReport(file, 0, skips, verified.rejections(),
                    "write failed: " + exception.getMessage(), false);
        }
        return new FileReport(file, verified.accepted().size(), skips, verified.rejections(), null, true);
    }

    private Verification verify(LegacyFileScanner.Result scan) {
        if (checker == null) {
            return new Verification(scan.changes(), List.of());
        }
        List<LegacyFileScanner.Change> accepted = new ArrayList<>(scan.changes().size());
        List<String> rejections = new ArrayList<>();
        for (LegacyFileScanner.Change change : scan.changes()) {
            String error = checker.check(change.newValue());
            if (error == null) {
                accepted.add(change);
            } else {
                rejections.add("line " + (change.lineIndex() + 1) + " -> " + error);
            }
        }
        return new Verification(List.copyOf(accepted), List.copyOf(rejections));
    }

    private record Verification(@NotNull List<LegacyFileScanner.Change> accepted,
            @NotNull List<String> rejections) {
    }

    private List<String> describeSkips(LegacyFileScanner.Result scan) {
        List<String> skips = new ArrayList<>(scan.skips().size());
        for (LegacyFileScanner.Skip skip : scan.skips()) {
            skips.add("line " + skip.lineNumber() + ": " + skip.oldId() + " (" + skip.reason() + ")");
        }
        return List.copyOf(skips);
    }

    private List<Path> yamlFiles(Path folder) {
        try (Stream<Path> stream = Files.walk(folder)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    })

                    .filter(path -> !path.getParent().getFileName().toString().equals("lang"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException | UncheckedIOException exception) {
            return List.of();
        }
    }

    private void writeMarker(Path directory, Report report) {
        String body = "migrated_at=" + Instant.now()
                + System.lineSeparator() + "files=" + report.convertedFiles()
                + System.lineSeparator() + "lines=" + report.convertedLines()
                + System.lineSeparator();
        writeQuietly(directory.resolve(MARKER), body);
    }

    private void writeQuietly(Path target, String body) {
        try {
            Files.writeString(target, body, StandardCharsets.UTF_8);
        } catch (IOException | UncheckedIOException exception) {

        }
    }

    public interface PipelineChecker {

        @Nullable String check(@NotNull String pipeline);
    }

    public record FileReport(@NotNull Path file,
            int convertedLines,
            @NotNull List<String> skipped,
            @NotNull List<String> rejected,
            @Nullable String failure,
            boolean changed) {

        public FileReport {
            skipped = skipped == null ? List.of() : List.copyOf(skipped);
            rejected = rejected == null ? List.of() : List.copyOf(rejected);
        }
    }

    public record Report(boolean alreadyMigrated, @NotNull List<FileReport> files) {

        public Report {
            files = files == null ? List.of() : List.copyOf(files);
        }

        static Report alreadyDone() {
            return new Report(true, List.of());
        }

        public int convertedFiles() {
            return (int) files.stream().filter(FileReport::changed).count();
        }

        public int convertedLines() {
            return files.stream().mapToInt(FileReport::convertedLines).sum();
        }

        public int failedFiles() {
            return (int) files.stream().filter(report -> report.failure() != null).count();
        }

        public int skippedLines() {
            return files.stream().mapToInt(report -> report.skipped().size()).sum();
        }

        public int rejectedLines() {
            return files.stream().mapToInt(report -> report.rejected().size()).sum();
        }

        public @NotNull List<String> describe() {
            if (alreadyMigrated || files.isEmpty()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            lines.add("Legacy action migration: converted " + convertedLines() + " line(s) in "
                    + convertedFiles() + " file(s); " + skippedLines() + " line(s) skipped; "
                    + rejectedLines() + " line(s) rejected; "
                    + failedFiles() + " file(s) left unchanged.");
            for (FileReport report : files) {
                if (report.failure() != null) {
                    lines.add("  " + report.file() + ": " + report.failure());
                }
                for (String skip : report.skipped()) {
                    lines.add("  " + report.file() + " " + skip);
                }
                for (String rejection : report.rejected()) {
                    lines.add("  " + report.file() + " " + rejection);
                }
            }
            return List.copyOf(lines);
        }
    }
}
