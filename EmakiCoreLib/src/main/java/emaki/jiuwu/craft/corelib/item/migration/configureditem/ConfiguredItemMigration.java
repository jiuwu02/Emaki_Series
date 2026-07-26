package emaki.jiuwu.craft.corelib.item.migration.configureditem;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.bukkit.plugin.java.JavaPlugin;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.BoostedYamlSection;
import emaki.jiuwu.craft.corelib.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;

public final class ConfiguredItemMigration {
    private static final DateTimeFormatter BACKUP_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final GeneralSettings GENERAL_SETTINGS = GeneralSettings.builder()
            .setUseDefaults(false)
            .build();
    private static final LoaderSettings LOADER_SETTINGS = LoaderSettings.builder()
            .setCreateFileIfAbsent(true)
            .setAutoUpdate(false)
            .build();
    private static final DumperSettings DUMPER_SETTINGS = DumperSettings.builder()
            .setIndentation(2)
            .build();
    private static final UpdaterSettings UPDATER_SETTINGS = UpdaterSettings.builder()
            .setAutoSave(false)
            .setKeepAll(true)
            .setOptionSorting(UpdaterSettings.OptionSorting.SORT_BY_DEFAULTS)
            .build();
    private static final Set<String> GUI_NODE_MAP_KEYS = Set.of("slots", "buttons", "content_templates", "virtual_items");
    private static final Set<String> GUI_SINGLE_NODE_KEYS = Set.of("content_item");

    private ConfiguredItemMigration() {
    }

    public static void migrateGuiFiles(JavaPlugin plugin, Collection<File> files) {
        Objects.requireNonNull(plugin, "plugin");
        MigrationReport report = migrateFiles(plugin.getDataFolder().toPath(), files, ConfiguredItemMigration::convertGui);
        for (FileIssue issue : report.skipped()) {
            plugin.getLogger().warning("Skipped configured-item YAML migration for "
                    + issue.file().getPath() + ": " + issue.message());
        }
        for (FileIssue issue : report.failures()) {
            plugin.getLogger().warning("Configured-item YAML migration failed for "
                    + issue.file().getPath() + ": " + issue.message()
                    + "; the original file will be loaded through legacy compatibility.");
        }
        if (report.changedFiles() > 0) {
            plugin.getLogger().info("Migrated " + report.changedFiles() + " GUI YAML file(s), "
                    + report.changedNodes() + " configured-item node(s). Backups: " + report.backupRoot());
        }
    }

    public static MigrationReport migrateFiles(Path dataFolder,
            Collection<File> files,
            Function<Map<String, Object>, Conversion> converter) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(converter, "converter");
        List<File> orderedFiles = new ArrayList<>(files == null ? List.of() : files);
        orderedFiles.removeIf(file -> file == null || !file.isFile());
        orderedFiles.sort((left, right) -> left.getPath().compareToIgnoreCase(right.getPath()));

        Path normalizedDataFolder = dataFolder.toAbsolutePath().normalize();
        Path backupRoot = null;
        int changedFiles = 0;
        int changedNodes = 0;
        List<FileIssue> failures = new ArrayList<>();
        List<FileIssue> skipped = new ArrayList<>();

        for (File file : orderedFiles) {
            try {
                EditableYaml yamlFile = openEditable(file);
                Map<String, Object> original = yamlFile.values();
                Conversion converted = converter.apply(MapYamlSection.normalizeMap(original));
                if (converted == null) {
                    continue;
                }
                if (Texts.isNotBlank(converted.skipReason())) {
                    skipped.add(new FileIssue(file, converted.skipReason()));
                    continue;
                }
                Map<String, Object> migrated = converted.values();
                if (Objects.equals(original, migrated)) {
                    continue;
                }
                validateInMemory(migrated);
                if (backupRoot == null) {
                    backupRoot = normalizedDataFolder
                            .resolve("migration-backups")
                            .resolve("configured-item-format")
                            .resolve(LocalDateTime.now().format(BACKUP_TIMESTAMP));
                }
                Path backup = createBackup(normalizedDataFolder, backupRoot, file.toPath());
                applyTopLevelChanges(yamlFile.document(), original, migrated);
                saveAtomic(file, yamlFile.document(), backup, migrated);
                changedFiles++;
                changedNodes += Math.max(1, converted.changedNodes());
            } catch (Exception exception) {
                failures.add(new FileIssue(file, safeMessage(exception)));
            }
        }
        return new MigrationReport(
                orderedFiles.size(),
                changedFiles,
                changedNodes,
                backupRoot,
                List.copyOf(failures),
                List.copyOf(skipped)
        );
    }

    private static Conversion convertGui(Map<String, Object> root) {
        Map<String, Object> original = MapYamlSection.normalizeMap(root);
        Map<String, Object> migrated = MapYamlSection.normalizeMap(original);
        Counter counter = new Counter();
        String skipReason = migrateGuiContainers(migrated, counter);
        if (Texts.isNotBlank(skipReason)) {
            return Conversion.skipped(original, skipReason);
        }
        return Objects.equals(original, migrated)
                ? Conversion.unchanged(original)
                : Conversion.changed(migrated, counter.value);
    }

    private static String migrateGuiContainers(Map<String, Object> container, Counter counter) {
        for (Map.Entry<String, Object> entry : new ArrayList<>(container.entrySet())) {
            String key = Texts.toStringSafe(entry.getKey()).toLowerCase(Locale.ROOT);
            Object raw = entry.getValue();
            if (GUI_NODE_MAP_KEYS.contains(key)) {
                Map<String, Object> nodes = mutableMap(raw);
                if (nodes == null) {
                    continue;
                }
                for (Map.Entry<String, Object> nodeEntry : new ArrayList<>(nodes.entrySet())) {
                    Map<String, Object> node = mutableMap(nodeEntry.getValue());
                    if (node == null) {
                        continue;
                    }
                    String skipReason = ConfiguredItemNodeConverter.migrateLegacyItemNodeInPlace(node, nodeEntry.getKey());
                    if (Texts.isNotBlank(skipReason)) {
                        return skipReason;
                    }
                    boolean itemChanged = !Objects.equals(nodeEntry.getValue(), node);
                    skipReason = migrateGuiContainers(node, counter);
                    if (Texts.isNotBlank(skipReason)) {
                        return skipReason;
                    }
                    nodes.put(nodeEntry.getKey(), node);
                    if (itemChanged) {
                        counter.value++;
                    }
                }
                container.put(entry.getKey(), nodes);
                continue;
            }
            if (GUI_SINGLE_NODE_KEYS.contains(key)) {
                Map<String, Object> node = mutableMap(raw);
                if (node == null) {
                    continue;
                }
                String skipReason = ConfiguredItemNodeConverter.migrateLegacyItemNodeInPlace(node, key);
                if (Texts.isNotBlank(skipReason)) {
                    return skipReason;
                }
                boolean itemChanged = !Objects.equals(raw, node);
                skipReason = migrateGuiContainers(node, counter);
                if (Texts.isNotBlank(skipReason)) {
                    return skipReason;
                }
                container.put(entry.getKey(), node);
                if (itemChanged) {
                    counter.value++;
                }
                continue;
            }
            if ("pages".equals(key)) {
                Map<String, Object> pages = mutableMap(raw);
                if (pages == null) {
                    continue;
                }
                for (Map.Entry<String, Object> pageEntry : new ArrayList<>(pages.entrySet())) {
                    Map<String, Object> page = mutableMap(pageEntry.getValue());
                    if (page == null) {
                        continue;
                    }
                    String skipReason = migrateGuiContainers(page, counter);
                    if (Texts.isNotBlank(skipReason)) {
                        return skipReason;
                    }
                    pages.put(pageEntry.getKey(), page);
                }
                container.put(entry.getKey(), pages);
            }
        }
        return "";
    }

    private static void validateInMemory(Map<String, Object> values) {
        String dumped = YamlFiles.dump(values);
        if (dumped.isBlank()) {
            throw new IllegalArgumentException("converted YAML could not be serialized");
        }
        Map<String, Object> reparsed = YamlFiles.load(dumped).asMap();
        if (!YamlFiles.dump(reparsed).equals(dumped)) {
            throw new IllegalArgumentException("converted YAML failed round-trip validation");
        }
    }

    private static EditableYaml openEditable(File file) throws IOException {
        Objects.requireNonNull(file, "file");
        Path target = file.toPath();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!file.exists()) {
            Files.createFile(target);
        }
        try (InputStream inputStream = Files.newInputStream(target)) {
            YamlDocument document = YamlDocument.create(
                    inputStream,
                    GENERAL_SETTINGS,
                    LOADER_SETTINGS,
                    DUMPER_SETTINGS,
                    UPDATER_SETTINGS
            );
            return new EditableYaml(document, new BoostedYamlSection(document).asMap());
        }
    }

    private static void saveAtomic(File file,
            YamlDocument document,
            Path recoveryBackup,
            Map<String, ?> expectedValues) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(expectedValues, "expectedValues");
        Path target = file.toPath();
        Path parent = target.getParent();
        Path tempDirectory = parent == null ? Path.of(".") : parent;
        Files.createDirectories(tempDirectory);
        Path temp = Files.createTempFile(tempDirectory, target.getFileName().toString(), ".migration.tmp");
        boolean moved = false;
        try {
            Files.writeString(temp, document.dump(DUMPER_SETTINGS), StandardCharsets.UTF_8);
            String expected = YamlFiles.dump(expectedValues);
            String actual = YamlFiles.dump(YamlFiles.load(temp.toFile()).asMap());
            if (expected.isBlank() || !expected.equals(actual)) {
                throw new IOException("Temporary YAML failed migration validation for '" + file.getPath() + "'.");
            }
            try {
                moveReplacing(temp, target);
                moved = true;
            } catch (IOException exception) {
                restoreBackup(recoveryBackup, target, exception);
                throw exception;
            }
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void restoreBackup(Path backup, Path target, IOException moveFailure) {
        if (backup == null || target == null || !Files.isRegularFile(backup)) {
            return;
        }
        try {
            Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException restoreFailure) {
            moveFailure.addSuppressed(restoreFailure);
        }
    }

    private static Path createBackup(Path dataFolder, Path backupRoot, Path source) throws IOException {
        Path normalizedSource = source.toAbsolutePath().normalize();
        Path relative = normalizedSource.startsWith(dataFolder)
                ? dataFolder.relativize(normalizedSource)
                : Path.of(normalizedSource.getFileName().toString());
        Path backup = backupRoot.resolve(relative).normalize();
        if (!backup.startsWith(backupRoot)) {
            throw new IOException("Refusing unsafe migration backup path for " + source);
        }
        Files.createDirectories(backup.getParent());
        Files.copy(normalizedSource, backup, StandardCopyOption.COPY_ATTRIBUTES);
        return backup;
    }

    private static void applyTopLevelChanges(YamlDocument document,
            Map<String, Object> original,
            Map<String, Object> migrated) {
        if (document == null) {
            throw new IllegalArgumentException("YAML document is unavailable");
        }
        for (String key : original.keySet()) {
            if (!migrated.containsKey(key)) {
                document.remove(key);
            }
        }
        for (Map.Entry<String, Object> entry : migrated.entrySet()) {
            if (!Objects.equals(original.get(entry.getKey()), entry.getValue())) {
                document.set(entry.getKey(), entry.getValue());
            }
        }
    }

    private static Map<String, Object> mutableMap(Object raw) {
        Object plain = ConfigNodes.toPlainData(raw);
        return plain instanceof Map<?, ?> map ? MapYamlSection.normalizeMap(map) : null;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || Texts.isBlank(throwable.getMessage())) {
            return throwable == null ? "unknown error" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage();
    }

    public record Conversion(Map<String, Object> values, int changedNodes, String skipReason) {

        public Conversion {
            values = MapYamlSection.normalizeMap(values);
            changedNodes = Math.max(0, changedNodes);
            skipReason = Texts.toStringSafe(skipReason);
        }

        public static Conversion unchanged(Map<String, ?> values) {
            return new Conversion(MapYamlSection.normalizeMap(values), 0, "");
        }

        public static Conversion changed(Map<String, ?> values, int changedNodes) {
            return new Conversion(MapYamlSection.normalizeMap(values), Math.max(1, changedNodes), "");
        }

        public static Conversion skipped(Map<String, ?> values, String reason) {
            return new Conversion(MapYamlSection.normalizeMap(values), 0, reason);
        }
    }

    public record MigrationReport(int scannedFiles,
            int changedFiles,
            int changedNodes,
            Path backupRoot,
            List<FileIssue> failures,
            List<FileIssue> skipped) {

        public MigrationReport {
            scannedFiles = Math.max(0, scannedFiles);
            changedFiles = Math.max(0, changedFiles);
            changedNodes = Math.max(0, changedNodes);
            failures = failures == null ? List.of() : List.copyOf(failures);
            skipped = skipped == null ? List.of() : List.copyOf(skipped);
        }
    }

    public record FileIssue(File file, String message) {

        public FileIssue {
            message = Texts.toStringSafe(message);
        }
    }

    private record EditableYaml(YamlDocument document, Map<String, Object> values) {

        private EditableYaml {
            values = MapYamlSection.normalizeMap(values);
        }
    }

    private static final class Counter {

        private int value;
    }
}
