package emaki.jiuwu.craft.strengthen.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

public final class BootstrapService {

    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml");
    private static final List<String> STATIC_DIRECTORIES = List.of("gui", "recipes");
    private static final List<String> LEGACY_DIRECTORIES = List.of("replace", "profiles", "rules", "materials");

    private final EmakiStrengthenPlugin plugin;
    private final MessageService messages;

    public BootstrapService(EmakiStrengthenPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void bootstrap() {
        messages.info("console.bootstrap_start");
        ensureDirectory(plugin.getDataFolder().toPath());
        cleanupLegacyDirectories();
        for (String file : VERSIONED_FILES) {
            ensureDefaultFile(file);
            mergeVersioned(file);
        }
        for (String directory : STATIC_DIRECTORIES) {
            for (String file : YamlFiles.listResourcePaths(plugin, directory)) {
                ensureDefaultFile(file);
            }
        }
        messages.info("console.bootstrap_complete");
    }

    private void cleanupLegacyDirectories() {
        for (String relativePath : LEGACY_DIRECTORIES) {
            Path path = plugin.dataPath(relativePath);
            if (!Files.exists(path)) {
                continue;
            }
            try (var stream = Files.walk(path)) {
                stream.sorted(java.util.Comparator.reverseOrder())
                        .forEach(target -> {
                            try {
                                Files.deleteIfExists(target);
                            } catch (IOException exception) {
                                messages.warning("console.bootstrap_legacy_cleanup_failed", Map.of(
                                        "path", relativePath,
                                        "error", String.valueOf(exception.getMessage())
                                ));
                            }
                        });
            } catch (IOException exception) {
                messages.warning("console.bootstrap_legacy_cleanup_failed", Map.of(
                        "path", relativePath,
                        "error", String.valueOf(exception.getMessage())
                ));
            }
        }
    }

    private void mergeVersioned(String relativePath) {
        String versionKey = relativePath.startsWith("lang/") ? "lang_version" : "config_version";
        try {
            YamlFiles.syncVersionedResource(plugin, plugin.dataPath(relativePath).toFile(), relativePath, versionKey);
        } catch (IOException exception) {
            messages.warning("console.bootstrap_save_failed", Map.of(
                    "path", relativePath,
                    "error", String.valueOf(exception.getMessage())
            ));
        }
    }

    private void ensureDefaultFile(String relativePath) {
        Path target = plugin.dataPath(relativePath);
        if (target.toFile().exists()) {
            return;
        }
        try {
            if (!YamlFiles.copyResourceIfMissing(plugin, relativePath, target.toFile())) {
                messages.warning("console.default_file_missing", Map.of("path", relativePath));
            }
        } catch (IOException exception) {
            messages.warning("console.bootstrap_copy_failed", Map.of(
                    "path", relativePath,
                    "error", String.valueOf(exception.getMessage())
            ));
        }
    }

    private void ensureDirectory(Path path) {
        if (path == null) {
            return;
        }
        try {
            YamlFiles.ensureDirectory(path);
        } catch (IOException exception) {
            messages.warning("console.directory_create_failed", Map.of("path", path.toString()));
        }
    }
}
