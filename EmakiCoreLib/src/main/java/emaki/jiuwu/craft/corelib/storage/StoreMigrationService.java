package emaki.jiuwu.craft.corelib.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.async.AsyncFileService.FileScope;

public final class StoreMigrationService {

    private static final int MAX_DIFFERENCES = 50;
    private static final String BACKUP_SUFFIX = ".bak";
    private static final String BINARY_SUFFIX = ".bin";
    private static final String SQLITE_SUFFIX = ".db";

    public record MigrationResult(boolean migrated,
            boolean backupCreated,
            Path source,
            Path backup,
            Path target,
            int entryCount) {
    }

    public record VerificationResult(boolean valid, int entryCount, List<String> differences) {

        public VerificationResult {
            differences = differences == null ? List.of() : List.copyOf(differences);
        }
    }

    public record RollbackResult(boolean restored, Path source, Path backup, List<Path> removedTargets) {

        public RollbackResult {
            removedTargets = removedTargets == null ? List.of() : List.copyOf(removedTargets);
        }
    }

    private final FileScope scope;

    public StoreMigrationService(FileScope scope) {
        this.scope = Objects.requireNonNull(scope, "scope");
    }

    public static Path backupPath(Path source) {
        return source.resolveSibling(source.getFileName() + BACKUP_SUFFIX);
    }

    public static Path targetPath(Path source, StoreFormat format) {
        return switch (format) {
            case YAML -> source;
            case BINARY -> source.resolveSibling(source.getFileName() + BINARY_SUFFIX);
            case SQLITE -> source.resolveSibling(source.getFileName() + SQLITE_SUFFIX);
        };
    }

    public CompletableFuture<MigrationResult> migrate(Path source, StoreFormat targetFormat) {
        Path normalized = normalize(source);
        return scope.read(normalized, taskName(normalized, "migrate"), () -> runMigrate(normalized, targetFormat));
    }

    public CompletableFuture<VerificationResult> verify(Path source, StoreFormat targetFormat) {
        Path normalized = normalize(source);
        return scope.read(normalized, taskName(normalized, "verify"), () -> runVerify(normalized, targetFormat));
    }

    public CompletableFuture<Path> dump(Path source, StoreFormat targetFormat, Path dumpFile) {
        Path normalized = normalize(source);
        Path normalizedDump = normalize(dumpFile);
        return scope.read(normalized, taskName(normalized, "dump"),
                () -> runDump(normalized, targetFormat, normalizedDump));
    }

    public CompletableFuture<RollbackResult> rollback(Path source) {
        Path normalized = normalize(source);
        return scope.read(normalized, taskName(normalized, "rollback"), () -> runRollback(normalized));
    }

    private MigrationResult runMigrate(Path source, StoreFormat targetFormat) {
        if (targetFormat == StoreFormat.YAML) {
            throw new StorageException("迁移目标格式必须为 binary 或 sqlite");
        }
        if (!Files.exists(source)) {
            throw new StorageException("源文件不存在: " + source);
        }
        Path backup = backupPath(source);
        boolean backupCreated = false;
        if (!Files.exists(backup)) {
            copy(source, backup);
            backupCreated = true;
        }
        Map<String, Object> document = YamlFiles.load(source.toFile()).asMap();
        Map<String, byte[]> entries = YamlEntryCodec.encode(document);
        Path target = targetPath(source, targetFormat);
        writeEntries(target, targetFormat, entries);
        return new MigrationResult(true, backupCreated, source, backup, target, entries.size());
    }

    private VerificationResult runVerify(Path source, StoreFormat targetFormat) {
        if (targetFormat == StoreFormat.YAML) {
            throw new StorageException("校验目标格式必须为 binary 或 sqlite");
        }
        Path target = targetPath(source, targetFormat);
        if (!Files.exists(target)) {
            throw new StorageException("未找到目标存储文件: " + target);
        }
        Map<String, Object> expected = YamlFiles.load(source.toFile()).asMap();
        Map<String, Object> actual = YamlEntryCodec.decode(readEntries(target, targetFormat));
        List<String> differences = new ArrayList<>();
        collectDifferences("", expected, actual, differences);
        return new VerificationResult(differences.isEmpty(), actual.size(), differences);
    }

    private Path runDump(Path source, StoreFormat targetFormat, Path dumpFile) {
        if (targetFormat == StoreFormat.YAML) {
            throw new StorageException("转储目标格式必须为 binary 或 sqlite");
        }
        Path target = targetPath(source, targetFormat);
        if (!Files.exists(target)) {
            throw new StorageException("未找到目标存储文件: " + target);
        }
        Map<String, Object> document = YamlEntryCodec.decode(readEntries(target, targetFormat));
        try {
            YamlFiles.save(dumpFile.toFile(), document);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return dumpFile;
    }

    private RollbackResult runRollback(Path source) {
        Path backup = backupPath(source);
        if (!Files.exists(backup)) {
            throw new StorageException("未找到备份文件: " + backup);
        }
        copy(backup, source);
        List<Path> removed = new ArrayList<>();
        List<Path> candidates = List.of(
                targetPath(source, StoreFormat.BINARY),
                targetPath(source, StoreFormat.SQLITE)
        );
        for (Path candidate : candidates) {
            try {
                if (Files.deleteIfExists(candidate)) {
                    removed.add(candidate);
                }
                for (String suffix : List.of("-wal", "-shm")) {
                    Files.deleteIfExists(candidate.resolveSibling(candidate.getFileName() + suffix));
                }
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        return new RollbackResult(true, source, backup, removed);
    }

    private void writeEntries(Path target, StoreFormat format, Map<String, byte[]> entries) {
        switch (format) {
            case BINARY -> BinaryFileFormat.write(target, entries);
            case SQLITE -> SqliteFileFormat.write(target, entries);
            case YAML -> throw new StorageException("迁移目标格式必须为 binary 或 sqlite");
        }
    }

    private Map<String, byte[]> readEntries(Path target, StoreFormat format) {
        return switch (format) {
            case BINARY -> BinaryFileFormat.read(target);
            case SQLITE -> SqliteFileFormat.read(target);
            case YAML -> throw new StorageException("迁移目标格式必须为 binary 或 sqlite");
        };
    }

    private static void collectDifferences(String prefix, Object expected, Object actual, List<String> differences) {
        if (differences.size() >= MAX_DIFFERENCES) {
            return;
        }
        if (expected instanceof Map<?, ?> expectedMap && actual instanceof Map<?, ?> actualMap) {
            Set<String> keys = new LinkedHashSet<>();
            expectedMap.keySet().forEach(key -> keys.add(String.valueOf(key)));
            actualMap.keySet().forEach(key -> keys.add(String.valueOf(key)));
            for (String key : keys) {
                if (differences.size() >= MAX_DIFFERENCES) {
                    return;
                }
                String path = prefix.isEmpty() ? key : prefix + "." + key;
                boolean hasExpected = expectedMap.containsKey(key);
                if (!hasExpected) {
                    differences.add(path + " 多余");
                    continue;
                }
                if (!actualMap.containsKey(key)) {
                    differences.add(path + " 缺失");
                    continue;
                }
                collectDifferences(path, expectedMap.get(key), actualMap.get(key), differences);
            }
            return;
        }
        if (expected instanceof List<?> expectedList && actual instanceof List<?> actualList) {
            if (expectedList.size() != actualList.size()) {
                differences.add(prefix + " 列表长度 " + expectedList.size() + " != " + actualList.size());
                return;
            }
            for (int index = 0; index < expectedList.size(); index++) {
                if (differences.size() >= MAX_DIFFERENCES) {
                    return;
                }
                collectDifferences(prefix + "[" + index + "]", expectedList.get(index), actualList.get(index), differences);
            }
            return;
        }
        if (!valueEquals(expected, actual)) {
            differences.add(prefix + " " + display(expected) + " != " + display(actual));
        }
    }

    private static boolean valueEquals(Object expected, Object actual) {
        if (expected instanceof Number expectedNumber && actual instanceof Number actualNumber) {
            return new BigDecimal(expectedNumber.toString()).compareTo(new BigDecimal(actualNumber.toString())) == 0;
        }
        return Objects.equals(expected, actual);
    }

    private static String display(Object value) {
        if (value instanceof Map<?, ?> map) {
            return "映射(" + map.size() + ")";
        }
        if (value instanceof List<?> list) {
            return "列表(" + list.size() + ")";
        }
        return String.valueOf(value);
    }

    private static void copy(Path from, Path to) {
        try {
            Path parent = to.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }

    private static String taskName(Path source, String action) {
        return "storage-" + action + ":" + source.getFileName();
    }
}
