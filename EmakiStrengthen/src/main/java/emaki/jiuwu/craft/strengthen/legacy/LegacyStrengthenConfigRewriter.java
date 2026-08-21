package emaki.jiuwu.craft.strengthen.legacy;

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
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.strengthen.legacy.LegacyMatchRuleConverter.Conversion;
import emaki.jiuwu.craft.strengthen.legacy.LegacyMatchRuleConverter.LegacyMatchRule;

public final class LegacyStrengthenConfigRewriter {

    private static final String LEGACY_KEY = "match";
    private static final String MATCHER_KEY = "matcher";
    private static final List<String> PROMOTED_ORDER = List.of(
            LegacyMatchRuleConverter.FIELD_SLOT_GROUPS,
            LegacyMatchRuleConverter.FIELD_STATS_ANY,
            LegacyMatchRuleConverter.FIELD_SOURCE_PATTERNS);

    private final Path directory;
    private final Logger logger;

    public LegacyStrengthenConfigRewriter(@NotNull Path directory, @NotNull Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    public enum Status {
        CONVERTED,
        NO_LEGACY_BLOCK,
        CONFLICT,
        UNCONVERTIBLE,
        FAILED
    }

    public record FileReport(String fileName, Status status, String detail, List<String> diff, String backupName) {

        public FileReport {
            fileName = Texts.toStringSafe(fileName);
            detail = Texts.toStringSafe(detail);
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

        public boolean hasConvertible() {
            return count(Status.CONVERTED) > 0L;
        }
    }

    public @NotNull RunReport run(boolean apply) {
        List<FileReport> reports = new ArrayList<>();
        for (Path file : listConfigFiles()) {
            reports.add(processFile(file, apply));
        }
        return new RunReport(apply, reports);
    }

    private List<Path> listConfigFiles() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = Texts.lower(path.getFileName().toString());
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException failure) {
            logger.warning("无法列出待转换目录 " + directory + ": " + failure.getMessage());
            return List.of();
        }
    }

    private FileReport processFile(Path file, boolean apply) {
        String fileName = file.getFileName().toString();
        List<String> lines;
        YamlSection section;
        try {
            lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
            section = YamlFiles.load(file.toFile());
        } catch (IOException | RuntimeException failure) {
            logger.warning("读取配置失败 " + fileName + ": " + failure.getMessage());
            return new FileReport(fileName, Status.FAILED, String.valueOf(failure.getMessage()), List.of(), "");
        }
        if (section == null || !section.contains(LEGACY_KEY)) {
            return new FileReport(fileName, Status.NO_LEGACY_BLOCK, "", List.of(), "");
        }
        YamlSection legacy = section.getSection(LEGACY_KEY);
        if (legacy == null) {
            return new FileReport(fileName, Status.FAILED, LEGACY_KEY + " 不是配置节", List.of(), "");
        }
        LegacyMatchRule rule = readRule(legacy);
        Conversion conversion = LegacyMatchRuleConverter.convert(rule);
        if (!conversion.complete()) {
            return new FileReport(fileName, Status.UNCONVERTIBLE,
                    String.join(", ", conversion.unconvertibleFields()), List.of(), "");
        }
        String conflict = detectConflict(section, conversion);
        if (Texts.isNotBlank(conflict)) {
            return new FileReport(fileName, Status.CONFLICT, conflict, List.of(), "");
        }
        int[] block = findTopLevelBlock(lines, LEGACY_KEY);
        if (block == null) {
            return new FileReport(fileName, Status.FAILED, "无法在文本中定位 " + LEGACY_KEY + " 块", List.of(), "");
        }
        List<String> replacement = renderFragment(conversion);
        List<String> removed = List.copyOf(lines.subList(block[0], block[1]));
        List<String> rewritten = new ArrayList<>(lines.subList(0, block[0]));
        rewritten.addAll(replacement);
        rewritten.addAll(lines.subList(block[1], lines.size()));
        List<String> diff = buildDiff(removed, replacement);
        if (!apply) {
            return new FileReport(fileName, Status.CONVERTED, "", diff, "");
        }
        try {
            String backupName = backup(file);
            writeLines(file, rewritten);
            return new FileReport(fileName, Status.CONVERTED, "", diff, backupName);
        } catch (IOException failure) {
            logger.warning("写入配置失败 " + fileName + ": " + failure.getMessage());
            return new FileReport(fileName, Status.FAILED, String.valueOf(failure.getMessage()), diff, "");
        }
    }

    private static LegacyMatchRule readRule(YamlSection legacy) {
        return new LegacyMatchRule(
                legacy.getStringList(LegacyMatchRuleConverter.FIELD_SOURCE_TYPES),
                legacy.getStringList(LegacyMatchRuleConverter.FIELD_SOURCE_IDS),
                legacy.getStringList(LegacyMatchRuleConverter.FIELD_SOURCE_PATTERNS),
                legacy.getStringList(LegacyMatchRuleConverter.FIELD_SLOT_GROUPS),
                legacy.getStringList(LegacyMatchRuleConverter.FIELD_LORE_CONTAINS),
                legacy.getStringList(LegacyMatchRuleConverter.FIELD_STATS_ANY));
    }

    private static String detectConflict(YamlSection section, Conversion conversion) {
        List<String> conflicts = new ArrayList<>();
        if (conversion.matcher() != null && section.contains(MATCHER_KEY)) {
            conflicts.add(MATCHER_KEY);
        }
        for (String key : conversion.promotedFields().keySet()) {
            if (section.contains(key)) {
                conflicts.add(key);
            }
        }
        return String.join(", ", conflicts);
    }

    static @Nullable int[] findTopLevelBlock(List<String> lines, String key) {
        int start = -1;
        String prefix = key + ":";
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.startsWith(prefix) && (line.length() == prefix.length()
                    || Character.isWhitespace(line.charAt(prefix.length())))) {
                start = index;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        int end = lines.size();
        for (int index = start + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            if (Character.isWhitespace(line.charAt(0))) {
                continue;
            }
            end = index;
            break;
        }
        while (end > start + 1 && isTrailingSeparator(lines.get(end - 1))) {
            end--;
        }
        return new int[] {start, end};
    }

    private static boolean isTrailingSeparator(String line) {
        return line.isBlank() || line.startsWith("#");
    }

    private static List<String> renderFragment(Conversion conversion) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (conversion.matcher() != null) {
            values.put(MATCHER_KEY, conversion.matcher());
        }
        for (String key : PROMOTED_ORDER) {
            List<String> value = conversion.promotedFields().get(key);
            if (value != null && !value.isEmpty()) {
                values.put(key, value);
            }
        }
        if (values.isEmpty()) {
            return List.of();
        }
        List<String> rendered = new ArrayList<>(YamlFiles.dump(values).lines().toList());
        while (!rendered.isEmpty() && rendered.getLast().isBlank()) {
            rendered.removeLast();
        }
        return List.copyOf(rendered);
    }

    private static List<String> buildDiff(List<String> removed, List<String> added) {
        List<String> diff = new ArrayList<>();
        for (String line : removed) {
            diff.add("- " + line);
        }
        for (String line : added) {
            diff.add("+ " + line);
        }
        return List.copyOf(diff);
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

    private static void writeLines(Path file, List<String> lines) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + ".converting.tmp");
        boolean moved = false;
        try {
            Files.writeString(temp, String.join(System.lineSeparator(), lines) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }
}
