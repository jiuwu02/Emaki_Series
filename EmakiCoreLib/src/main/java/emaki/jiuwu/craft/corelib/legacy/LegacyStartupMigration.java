package emaki.jiuwu.craft.corelib.legacy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.text.LogMessages;

public final class LegacyStartupMigration {

    private static final String FILE_MIGRATED_KEY = "console.legacy_file_migrated";
    private static final String FILE_FAILED_KEY = "console.legacy_migration_failed";
    private static final String SUMMARY_KEY = "console.legacy_migration_summary";

    private LegacyStartupMigration() {
    }

    public record Entry(@NotNull String path, @NotNull String detail) {

        public Entry {
            path = Texts.toStringSafe(path);
            detail = Texts.toStringSafe(detail);
        }
    }

    public record Result(@NotNull List<Entry> migrated, @NotNull List<Entry> failures) {

        public Result {
            migrated = migrated == null ? List.of() : List.copyOf(migrated);
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        public static @NotNull Result empty() {
            return new Result(List.of(), List.of());
        }

        public boolean changed() {
            return !migrated.isEmpty();
        }
    }

    public static @NotNull Result run(@NotNull String scope,
            LogMessages messages,
            @NotNull Logger logger,
            @NotNull Supplier<Result> task) {
        Result result;
        try {
            result = task.get();
        } catch (RuntimeException failure) {
            logger.warning("[migrate] " + scope + " 自动迁移失败: " + failure.getMessage());
            if (messages != null) {
                messages.warning(FILE_FAILED_KEY, Map.of(
                        "path", scope,
                        "error", String.valueOf(failure.getMessage())
                ));
            }
            return Result.empty();
        }
        if (result == null) {
            return Result.empty();
        }
        if (messages != null) {
            for (Entry entry : result.migrated()) {
                messages.info(FILE_MIGRATED_KEY, Map.of("path", entry.path(), "detail", entry.detail()));
            }
            for (Entry entry : result.failures()) {
                messages.warning(FILE_FAILED_KEY, Map.of("path", entry.path(), "error", entry.detail()));
            }
            if (result.changed()) {
                messages.info(SUMMARY_KEY, Map.of(
                        "scope", scope,
                        "count", result.migrated().size(),
                        "files", result.migrated().stream().map(Entry::path).reduce((left, right) -> left + ", " + right).orElse("")
                ));
            }
        }
        return result;
    }

    public record FileOutcome(@NotNull String path, @NotNull String status, @NotNull String detail) {

        public FileOutcome {
            path = Texts.toStringSafe(path);
            status = Texts.toStringSafe(status);
            detail = Texts.toStringSafe(detail);
        }
    }

    private static final java.util.Set<String> CONVERTED_STATUSES = java.util.Set.of("CONVERTED");
    private static final java.util.Set<String> FAILED_STATUSES = java.util.Set.of("FAILED", "CONFLICT", "UNCONVERTIBLE");

    public static @NotNull Result of(@NotNull List<FileOutcome> outcomes) {
        List<Entry> migrated = new ArrayList<>();
        List<Entry> failures = new ArrayList<>();
        for (FileOutcome outcome : outcomes) {
            String status = outcome.status().toUpperCase(java.util.Locale.ROOT);
            if (CONVERTED_STATUSES.contains(status)) {
                migrated.add(new Entry(outcome.path(), outcome.detail()));
            } else if (FAILED_STATUSES.contains(status)) {
                failures.add(new Entry(outcome.path(), outcome.detail()));
            }
        }
        return new Result(migrated, failures);
    }

    public static @NotNull Result fromItemSourceReport(@NotNull LegacyItemSourceRewriter.RunReport report) {
        List<Entry> migrated = new ArrayList<>();
        List<Entry> failures = new ArrayList<>();
        for (LegacyItemSourceRewriter.FileReport file : report.files()) {
            if (file.status() == LegacyItemSourceRewriter.Status.CONVERTED) {
                migrated.add(new Entry(file.fileName(), file.occurrences() + " 处"));
            } else if (file.status() == LegacyItemSourceRewriter.Status.FAILED
                    || file.status() == LegacyItemSourceRewriter.Status.CONFLICT
                    || file.status() == LegacyItemSourceRewriter.Status.UNCONVERTIBLE) {
                failures.add(new Entry(file.fileName(), file.detail()));
            }
        }
        return new Result(migrated, failures);
    }
}
