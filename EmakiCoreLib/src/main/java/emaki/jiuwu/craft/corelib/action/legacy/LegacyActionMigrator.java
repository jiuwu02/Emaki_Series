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

/**
 * The one-shot old-syntax migration.
 *
 * <p>Runs once per server, at the end of CoreLib's enable, then never again: a marker file records
 * completion so the decision does not depend on in-memory state or on a config key a server owner
 * might reset. Nothing here registers a listener, a command or a stage, so once the migration has run
 * the class is inert and the whole package can be deleted.</p>
 *
 * <p>Deliberately not exposed through {@code EmakiCoreLibApi}: third parties must not be able to build
 * on a component whose entire purpose is to be removed.</p>
 */
public final class LegacyActionMigrator {

    /** Marker filename, hidden so it survives config regeneration. */
    private static final String MARKER = ".action-v2-migrated";

    /** Suffix for the copy kept of every rewritten file. */
    private static final String BACKUP_SUFFIX = ".legacy-backup";

    /** Suffix for output that failed its compile check and was therefore not installed. */
    private static final String FAILED_SUFFIX = ".v2-failed";

    private final LegacyFileScanner scanner = new LegacyFileScanner();
    private final PipelineChecker checker;

    /**
     * Creates a migrator.
     *
     * @param checker compiles a candidate line, refusing output that the v2 engine cannot read; when
     *     {@code null} the compile check is skipped and every converted file is reported as unverified
     */
    public LegacyActionMigrator(@Nullable PipelineChecker checker) {
        this.checker = checker;
    }

    /**
     * Runs the migration unless the marker says it already ran.
     *
     * @param markerDirectory CoreLib's data folder, where the marker lives
     * @param dataFolders the plugin data folders to scan
     * @param dryRun when {@code true} nothing is written and the report describes what would change
     * @return the summary to log
     */
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
        // The marker is withheld while anything still needs another attempt. A rejected line is a gap in
        // this converter's own mapping, and an unreadable or unwritable file is a transient problem, so
        // both must be retried after the next update. A recognised-but-unmappable id does not withhold
        // it: no change to this table can convert a line whose target stage was never built.
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
        // The compile check is the safety valve: it is the only thing standing between a mapping bug and
        // a config file that silently stops working. It is applied per line rather than per file, because
        // failing the whole file also discards the lines that did convert, which is how one unmapped
        // argument name used to leave an entire config in old syntax.
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
            // Never overwritten. Withholding the marker means a file with rejected lines is visited again
            // after an update, and replacing the backup on that second pass would leave the owner holding
            // a copy of the already-half-migrated file instead of the original they wanted to compare to.
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

    /** Compiles every converted line, splitting the ones the v2 engine accepts from the ones it rejects. */
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

    /**
     * What the compile check made of one file's converted lines.
     *
     * @param accepted the changes whose converted line compiles
     * @param rejections one entry per line the v2 engine refused
     */
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
                    // Language files hold no actions and are large; skipping them keeps the scan honest
                    // about what it looked at.
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
            // Nothing useful to do: the migration itself already succeeded or already failed, and
            // throwing here would turn a bookkeeping problem into a failed server start.
        }
    }

    /** Compiles one candidate pipeline line. */
    public interface PipelineChecker {

        /**
         * Checks one line.
         *
         * @param pipeline the converted pipeline line
         * @return {@code null} when it compiles, otherwise a short description of the problem
         */
        @Nullable String check(@NotNull String pipeline);
    }

    /**
     * What happened to one file.
     *
     * @param file the file
     * @param convertedLines how many lines were rewritten
     * @param skipped one entry per recognised line that has no v2 counterpart
     * @param rejected one entry per converted line the compile check refused
     * @param failure why the file was left alone, {@code null} when it was not
     * @param changed whether the file was rewritten, or would be in a dry run
     */
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

    /**
     * The whole run.
     *
     * @param alreadyMigrated whether the marker short-circuited the run
     * @param files one entry per file that had something to convert, skip or report
     */
    public record Report(boolean alreadyMigrated, @NotNull List<FileReport> files) {

        public Report {
            files = files == null ? List.of() : List.copyOf(files);
        }

        static Report alreadyDone() {
            return new Report(true, List.of());
        }

        /** {@return how many files were or would be rewritten} */
        public int convertedFiles() {
            return (int) files.stream().filter(FileReport::changed).count();
        }

        /** {@return how many lines were or would be rewritten} */
        public int convertedLines() {
            return files.stream().mapToInt(FileReport::convertedLines).sum();
        }

        /** {@return how many files were left alone because something went wrong} */
        public int failedFiles() {
            return (int) files.stream().filter(report -> report.failure() != null).count();
        }

        /** {@return how many recognised lines have no v2 counterpart} */
        public int skippedLines() {
            return files.stream().mapToInt(report -> report.skipped().size()).sum();
        }

        /** {@return how many converted lines the compile check refused} */
        public int rejectedLines() {
            return files.stream().mapToInt(report -> report.rejected().size()).sum();
        }

        /**
         * Renders the one-line summary plus one line per problem.
         *
         * <p>The per-file detail is intentionally limited to problems. Silence about successes is what
         * "silent migration" means; silence about failures would leave a server owner with a config
         * that no longer works and no way to find out why. A run that found nothing at all says nothing:
         * on an all-v2 server the summary would otherwise be a permanent startup line reporting zeroes.</p>
         *
         * @return the lines to log
         */
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
