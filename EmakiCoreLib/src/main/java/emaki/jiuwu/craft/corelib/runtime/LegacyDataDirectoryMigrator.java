package emaki.jiuwu.craft.corelib.runtime;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.plugin.java.JavaPlugin;

public final class LegacyDataDirectoryMigrator {

    private LegacyDataDirectoryMigrator() {
    }

    public static void migrate(JavaPlugin plugin, String... legacyNames) {
        if (plugin == null || legacyNames == null || legacyNames.length == 0) {
            return;
        }
        Path targetRoot = plugin.getDataFolder().toPath();
        Path parent = targetRoot.getParent();
        if (parent == null) {
            return;
        }
        Consumer<String> info = message -> plugin.getLogger().info(message);
        Consumer<String> warn = message -> plugin.getLogger().warning(message);
        for (String legacyName : legacyNames) {
            if (legacyName == null || legacyName.isBlank()) {
                continue;
            }
            Path legacyRoot = parent.resolve(legacyName.trim());
            if (legacyRoot.equals(targetRoot) || !Files.exists(legacyRoot)) {
                continue;
            }
            migrate(targetRoot, legacyRoot, info, warn);
            return;
        }
    }

    static void migrate(Path targetRoot,
            Path legacyRoot,
            Consumer<String> info,
            Consumer<String> warn) {
        Objects.requireNonNull(targetRoot, "targetRoot");
        Objects.requireNonNull(legacyRoot, "legacyRoot");
        Consumer<String> safeInfo = info == null ? ignored -> {
        } : info;
        Consumer<String> safeWarn = warn == null ? ignored -> {
        } : warn;
        try {
            if (!Files.isDirectory(legacyRoot)) {
                return;
            }
            if (!Files.exists(targetRoot)) {
                Files.createDirectories(targetRoot.getParent());
                Files.move(legacyRoot, targetRoot);
                safeInfo.accept("Migrated legacy data directory from " + legacyRoot + " to " + targetRoot + ".");
                return;
            }
            if (!isDirectoryEmpty(targetRoot)) {
                safeWarn.accept("Legacy data directory " + legacyRoot + " was detected, but target directory " + targetRoot
                        + " already contains files. Skipping migration to avoid overwriting data.");
                return;
            }
            mergeMissingFiles(legacyRoot, targetRoot);
            deleteTreeIfEmpty(legacyRoot);
            safeInfo.accept("Merged legacy data directory from " + legacyRoot + " into " + targetRoot + ".");
        } catch (IOException exception) {
            safeWarn.accept("Failed to migrate legacy data directory from " + legacyRoot + " to " + targetRoot
                    + ": " + exception.getMessage());
        }
    }

    private static boolean isDirectoryEmpty(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return true;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            return !stream.iterator().hasNext();
        }
    }

    private static void mergeMissingFiles(Path sourceRoot, Path targetRoot) throws IOException {
        try {
            Files.walk(sourceRoot).forEach(source -> {
                try {
                    Path relative = sourceRoot.relativize(source);
                    if (relative.toString().isBlank()) {
                        return;
                    }
                    Path target = targetRoot.resolve(relative);
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                        return;
                    }
                    if (Files.exists(target)) {
                        return;
                    }
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                } catch (IOException exception) {
                    throw new DirectoryMigrationException(exception);
                }
            });
        } catch (DirectoryMigrationException exception) {
            throw exception.ioException();
        }
    }

    private static void deleteTreeIfEmpty(Path root) throws IOException {
        Path[] paths = Files.walk(root)
                .sorted((left, right) -> Integer.compare(right.getNameCount(), left.getNameCount()))
                .toArray(Path[]::new);
        for (Path path : paths) {
            if (!Files.exists(path)) {
                continue;
            }
            if (Files.isDirectory(path) && !isDirectoryEmpty(path)) {
                continue;
            }
            Files.deleteIfExists(path);
        }
    }

    private static final class DirectoryMigrationException extends RuntimeException {

        private DirectoryMigrationException(IOException cause) {
            super(cause);
        }

        private IOException ioException() {
            return (IOException) getCause();
        }
    }
}
