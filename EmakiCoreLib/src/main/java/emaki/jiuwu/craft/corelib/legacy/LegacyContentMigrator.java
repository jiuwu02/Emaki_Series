package emaki.jiuwu.craft.corelib.legacy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.text.LogMessages;

/**
 * 启动期内容文件旧写法自动迁移引擎.
 *
 * <p>按目录遍历玩家可编辑的 YAML 内容文件, 交给 {@link Rule} 做行级改写. 改写保持原文件的注释,
 * 缩进风格, 行尾序列与结尾换行; 落盘前先备份原文件为 {@code <name>.bak}, 失败时原文件保持不变.
 * 每份被迁移的文件都会输出一条含路径的日志, 便于服主确认哪些配置被自动改写.
 */
public final class LegacyContentMigrator {

    private static final String FILE_MIGRATED_KEY = "console.legacy_file_migrated";
    private static final String FILE_FAILED_KEY = "console.legacy_migration_failed";
    private static final String SUMMARY_KEY = "console.legacy_migration_summary";

    private final Path root;
    private final String directory;
    private final String scope;
    private final Rule rule;
    private final LogMessages messages;
    private final Logger logger;
    private final Set<String> skipFileNames;

    public LegacyContentMigrator(@NotNull Path root,
            @NotNull String directory,
            @NotNull String scope,
            @NotNull Rule rule,
            LogMessages messages,
            @NotNull Logger logger) {
        this(root, directory, scope, rule, messages, logger, Set.of());
    }

    /**
     * @param skipFileNames 目录内不属于本规则管辖的文件名 (忽略大小写), 例如物品目录里的
     *                      {@code pack.yml} 是分包元数据而不是物品定义
     */
    public LegacyContentMigrator(@NotNull Path root,
            @NotNull String directory,
            @NotNull String scope,
            @NotNull Rule rule,
            LogMessages messages,
            @NotNull Logger logger,
            @NotNull Set<String> skipFileNames) {
        this.root = root;
        this.directory = Texts.toStringSafe(directory);
        this.scope = Texts.toStringSafe(scope);
        this.rule = rule;
        this.messages = messages;
        this.logger = logger;
        this.skipFileNames = skipFileNames == null || skipFileNames.isEmpty()
                ? Set.of()
                : skipFileNames.stream().map(Texts::lower).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public enum Status {
        MIGRATED,
        NOTHING,
        FAILED
    }

    public record FileReport(String path, Status status, List<String> changes, String backupName) {

        public FileReport {
            path = Texts.toStringSafe(path);
            changes = changes == null ? List.of() : List.copyOf(changes);
            backupName = Texts.toStringSafe(backupName);
        }
    }

    public record RunReport(boolean applied, List<FileReport> files) {

        public RunReport {
            files = files == null ? List.of() : List.copyOf(files);
        }

        public long count(Status status) {
            return files.stream().filter(report -> report.status() == status).count();
        }

        public boolean migrated() {
            return count(Status.MIGRATED) > 0L;
        }
    }

    /** 行级改写规则; 返回原列表本身表示该文件无需改写. */
    @FunctionalInterface
    public interface Rule {

        @NotNull Outcome apply(@NotNull List<String> lines);
    }

    public record Outcome(@NotNull List<String> lines, @NotNull List<String> changes) {

        public Outcome {
            lines = lines == null ? List.of() : lines;
            changes = changes == null ? List.of() : List.copyOf(changes);
        }

        public boolean changed() {
            return !changes.isEmpty();
        }

        public static @NotNull Outcome unchanged(@NotNull List<String> lines) {
            return new Outcome(lines, List.of());
        }
    }

    public @NotNull RunReport run(boolean apply) {
        List<FileReport> reports = new ArrayList<>();
        for (Path file : listFiles()) {
            reports.add(processFile(file, apply));
        }
        RunReport report = new RunReport(apply, reports);
        if (apply && report.migrated() && messages != null) {
            List<String> paths = report.files().stream()
                    .filter(file -> file.status() == Status.MIGRATED)
                    .map(FileReport::path)
                    .toList();
            messages.info(SUMMARY_KEY, Map.of(
                    "scope", scope,
                    "count", paths.size(),
                    "files", String.join(", ", paths)
            ));
        }
        return report;
    }

    private List<Path> listFiles() {
        Path target = Texts.isBlank(directory) ? root : root.resolve(directory);
        if (!Files.isDirectory(target)) {
            return List.of();
        }
        try (var stream = Files.walk(target)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = Texts.lower(path.getFileName().toString());
                        return (name.endsWith(".yml") || name.endsWith(".yaml"))
                                && !skipFileNames.contains(name);
                    })
                    .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException failure) {
            logger.warning("[migrate] 无法列出待迁移目录 " + target + ": " + failure.getMessage());
            return List.of();
        }
    }

    private FileReport processFile(Path file, boolean apply) {
        String relative = relativeName(file);
        List<String> lines;
        try {
            lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException failure) {
            fail(relative, failure);
            return new FileReport(relative, Status.FAILED, List.of(), "");
        }
        Outcome outcome;
        try {
            outcome = rule.apply(List.copyOf(lines));
        } catch (RuntimeException failure) {
            fail(relative, failure);
            return new FileReport(relative, Status.FAILED, List.of(), "");
        }
        if (outcome == null || !outcome.changed()) {
            return new FileReport(relative, Status.NOTHING, List.of(), "");
        }
        if (!apply) {
            return new FileReport(relative, Status.MIGRATED, outcome.changes(), "");
        }
        try {
            String lineSeparator = detectLineSeparator(file);
            boolean trailingNewline = trailingNewline(file);
            String backupName = backup(file);
            writeLines(file, outcome.lines(), lineSeparator, trailingNewline);
            if (messages != null) {
                messages.info(FILE_MIGRATED_KEY, Map.of(
                        "path", relative,
                        "detail", String.join(", ", outcome.changes())
                ));
            }
            return new FileReport(relative, Status.MIGRATED, outcome.changes(), backupName);
        } catch (IOException failure) {
            fail(relative, failure);
            return new FileReport(relative, Status.FAILED, outcome.changes(), "");
        }
    }

    private void fail(String relative, Throwable failure) {
        logger.warning("[migrate] 自动迁移失败 " + relative + ": " + failure.getMessage());
        if (messages != null) {
            messages.warning(FILE_FAILED_KEY, Map.of(
                    "path", relative,
                    "error", String.valueOf(failure.getMessage())
            ));
        }
    }

    private String relativeName(Path file) {
        try {
            return root.relativize(file).toString().replace('\\', '/');
        } catch (IllegalArgumentException failure) {
            return file.getFileName().toString();
        }
    }

    private static String backup(Path file) throws IOException {
        Path candidate = file.resolveSibling(file.getFileName() + ".bak");
        int suffix = 2;
        while (Files.exists(candidate)) {
            candidate = file.resolveSibling(file.getFileName() + ".bak." + suffix);
            suffix++;
        }
        Files.copy(file, candidate, StandardCopyOption.COPY_ATTRIBUTES);
        return candidate.getFileName().toString();
    }

    private static String detectLineSeparator(Path file) throws IOException {
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        int index = raw.indexOf('\n');
        if (index < 0) {
            return System.lineSeparator();
        }
        return index > 0 && raw.charAt(index - 1) == '\r' ? "\r\n" : "\n";
    }

    private static boolean trailingNewline(Path file) throws IOException {
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        return raw.isEmpty() || raw.endsWith("\n");
    }

    private static void writeLines(Path file,
            List<String> lines,
            String lineSeparator,
            boolean trailingNewline) throws IOException {
        Files.writeString(file, String.join(lineSeparator, lines) + (trailingNewline ? lineSeparator : ""),
                StandardCharsets.UTF_8);
    }
}
