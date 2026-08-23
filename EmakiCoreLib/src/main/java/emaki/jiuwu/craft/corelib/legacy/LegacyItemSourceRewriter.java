package emaki.jiuwu.craft.corelib.legacy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class LegacyItemSourceRewriter {

    private final Path root;
    private final List<LegacyTargetSpec> specs;
    private final Logger logger;

    public LegacyItemSourceRewriter(@NotNull Path root,
            @NotNull List<LegacyTargetSpec> specs,
            @NotNull Logger logger) {
        this.root = root;
        this.specs = List.copyOf(specs);
        this.logger = logger;
    }

    public enum Status {
        CONVERTED,
        NO_LEGACY_BLOCK,
        CONFLICT,
        UNCONVERTIBLE,
        FAILED
    }

    public record FileReport(String fileName,
            Status status,
            String detail,
            int occurrences,
            List<String> diff,
            String backupName) {

        public FileReport {
            fileName = Texts.toStringSafe(fileName);
            detail = Texts.toStringSafe(detail);
            occurrences = Math.max(0, occurrences);
            diff = diff == null ? List.of() : List.copyOf(diff);
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

        public int occurrences() {
            return files.stream()
                    .filter(report -> report.status() == Status.CONVERTED)
                    .mapToInt(FileReport::occurrences)
                    .sum();
        }

        public boolean hasConvertible() {
            return count(Status.CONVERTED) > 0L;
        }

        public List<FileReport> convertible() {
            return files.stream().filter(report -> report.status() == Status.CONVERTED).toList();
        }
    }

    public @NotNull RunReport run(boolean apply) {
        List<FileReport> reports = new ArrayList<>();
        for (Map.Entry<Path, List<LegacyTargetSpec>> entry : groupByFile().entrySet()) {
            reports.add(processFile(entry.getKey(), entry.getValue(), apply));
        }
        return new RunReport(apply, reports);
    }

    private Map<Path, List<LegacyTargetSpec>> groupByFile() {
        Map<Path, List<LegacyTargetSpec>> grouped = new LinkedHashMap<>();
        for (LegacyTargetSpec spec : specs) {
            for (Path file : listFiles(spec.directory())) {
                grouped.computeIfAbsent(file, key -> new ArrayList<>()).add(spec);
            }
        }
        return grouped;
    }

    private List<Path> listFiles(String directory) {
        Path target = Texts.isBlank(directory) ? root : root.resolve(directory);
        if (Files.isRegularFile(target)) {
            return List.of(target);
        }
        if (!Files.isDirectory(target)) {
            return List.of();
        }
        try (var stream = Files.walk(target)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = Texts.lower(path.getFileName().toString());
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    })
                    .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException failure) {
            logger.warning("无法列出待转换目录 " + target + ": " + failure.getMessage());
            return List.of();
        }
    }

    private FileReport processFile(Path file, List<LegacyTargetSpec> fileSpecs, boolean apply) {
        String fileName = relativeName(file);
        List<String> lines;
        try {
            lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException failure) {
            logger.warning("读取配置失败 " + fileName + ": " + failure.getMessage());
            return new FileReport(fileName, Status.FAILED, String.valueOf(failure.getMessage()), 0, List.of(), "");
        }
        List<Replacement> replacements = new ArrayList<>();
        List<String> unconvertible = new ArrayList<>();
        for (LegacyTargetSpec spec : fileSpecs) {
            collect(lines, spec, replacements, unconvertible);
        }
        if (replacements.isEmpty()) {
            return unconvertible.isEmpty()
                    ? new FileReport(fileName, Status.NO_LEGACY_BLOCK, "", 0, List.of(), "")
                    : new FileReport(fileName, Status.UNCONVERTIBLE, String.join(", ", unconvertible),
                            0, List.of(), "");
        }
        replacements.sort(Comparator.comparingInt((Replacement value) -> value.hit().startLine()).reversed());
        if (overlapping(replacements)) {
            return new FileReport(fileName, Status.CONFLICT, "命中区间重叠", 0, List.of(), "");
        }
        List<String> rewritten = new ArrayList<>(lines);
        List<String> diff = new ArrayList<>();
        for (Replacement replacement : replacements) {
            applyReplacement(rewritten, replacement, diff);
        }
        if (!apply) {
            return new FileReport(fileName, Status.CONVERTED, "", replacements.size(), diff, "");
        }
        try {
            String backupName = backup(file);
            writeLines(file, rewritten, detectLineSeparator(file), trailingNewline(file));
            return new FileReport(fileName, Status.CONVERTED, "", replacements.size(), diff, backupName);
        } catch (IOException failure) {
            logger.warning("写入配置失败 " + fileName + ": " + failure.getMessage());
            return new FileReport(fileName, Status.FAILED, String.valueOf(failure.getMessage()),
                    replacements.size(), diff, "");
        }
    }

    private void collect(List<String> lines,
            LegacyTargetSpec spec,
            List<Replacement> replacements,
            List<String> unconvertible) {
        for (YamlBlockLocator.Match match : YamlBlockLocator.locateWithSibling(lines, spec)) {
            YamlBlockLocator.Hit hit = match.legacy();
            if (hit.inline() && !inlineList(hit.inlineValue())) {
                unconvertible.add(spec.legacyKey() + " 为行内映射写法，请改为块状写法后重试");
                continue;
            }
            List<String> sources = LegacyMatcherFragment.parseSources(lines, hit);
            if (sources.isEmpty()) {
                unconvertible.add(spec.legacyKey() + " 未解析出物品源");
                continue;
            }
            YamlBlockLocator.Hit matcherHit = spec.mergeMode() == LegacyTargetSpec.MergeMode.MERGE_AND
                    ? match.matcher()
                    : null;
            List<String> existing = matcherHit == null
                    ? List.of()
                    : List.copyOf(lines.subList(matcherHit.startLine() + 1, matcherHit.endLine()));
            List<String> rendered = LegacyMatcherFragment.render(sources, hit, spec.matcherKey(), existing);
            if (rendered.isEmpty()) {
                unconvertible.add(spec.legacyKey() + " 渲染结果为空");
                continue;
            }
            replacements.add(new Replacement(hit, matcherHit, rendered));
        }
    }

    private static boolean inlineList(String value) {
        String trimmed = Texts.toStringSafe(value).trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]");
    }

    private static boolean overlapping(List<Replacement> replacements) {
        List<int[]> ranges = new ArrayList<>();
        for (Replacement replacement : replacements) {
            ranges.add(new int[] {replacement.hit().startLine(), replacement.hit().endLine()});
            if (replacement.matcherHit() != null) {
                ranges.add(new int[] {replacement.matcherHit().startLine(), replacement.matcherHit().endLine()});
            }
        }
        ranges.sort(Comparator.comparingInt(range -> range[0]));
        for (int index = 1; index < ranges.size(); index++) {
            if (ranges.get(index)[0] < ranges.get(index - 1)[1]) {
                return true;
            }
        }
        return false;
    }

    private static void applyReplacement(List<String> lines, Replacement replacement, List<String> diff) {
        YamlBlockLocator.Hit matcherHit = replacement.matcherHit();
        if (matcherHit != null && matcherHit.startLine() > replacement.hit().startLine()) {
            record(diff, lines.subList(matcherHit.startLine(), matcherHit.endLine()), List.of());
            lines.subList(matcherHit.startLine(), matcherHit.endLine()).clear();
        }
        record(diff, lines.subList(replacement.hit().startLine(), replacement.hit().endLine()),
                replacement.rendered());
        lines.subList(replacement.hit().startLine(), replacement.hit().endLine()).clear();
        lines.addAll(replacement.hit().startLine(), replacement.rendered());
        if (matcherHit != null && matcherHit.startLine() < replacement.hit().startLine()) {
            record(diff, lines.subList(matcherHit.startLine(), matcherHit.endLine()), List.of());
            lines.subList(matcherHit.startLine(), matcherHit.endLine()).clear();
        }
    }

    private static void record(List<String> diff, List<String> removed, List<String> added) {
        for (String line : removed) {
            diff.add("- " + line);
        }
        for (String line : added) {
            diff.add("+ " + line);
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
        Path temp = file.resolveSibling(file.getFileName() + ".converting.tmp");
        boolean moved = false;
        try {
            Files.writeString(temp, String.join(lineSeparator, lines) + (trailingNewline ? lineSeparator : ""),
                    StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private record Replacement(YamlBlockLocator.Hit hit,
            YamlBlockLocator.Hit matcherHit,
            List<String> rendered) {
    }
}
