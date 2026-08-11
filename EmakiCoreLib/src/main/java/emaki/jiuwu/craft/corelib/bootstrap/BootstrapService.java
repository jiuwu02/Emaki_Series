package emaki.jiuwu.craft.corelib.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.yaml.VersionedYamlFile;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;

public final class BootstrapService {

    private static final String VERSION_KEY = "version";

    private final JavaPlugin plugin;
    private final LogMessages messages;
    private final List<String> versionedFiles;
    private final List<String> staticFiles;
    private final List<String> defaultDataFiles;
    private final List<String> extraDirectories;
    private final BootstrapHooks hooks;

    public BootstrapService(JavaPlugin plugin,
            LogMessages messages,
            List<String> versionedFiles,
            List<String> staticFiles,
            List<String> defaultDataFiles,
            List<String> extraDirectories,
            BootstrapHooks hooks) {
        this.plugin = plugin;
        this.messages = messages;
        this.versionedFiles = versionedFiles == null ? List.of() : List.copyOf(versionedFiles);
        this.staticFiles = staticFiles == null ? List.of() : List.copyOf(staticFiles);
        this.defaultDataFiles = defaultDataFiles == null ? List.of() : List.copyOf(defaultDataFiles);
        this.extraDirectories = extraDirectories == null ? List.of() : List.copyOf(extraDirectories);
        this.hooks = hooks == null ? new BootstrapHooks() {
        } : hooks;
    }

    public synchronized boolean bootstrap() {
        info("console.bootstrap_start");
        hooks.beforeBootstrap();
        ensureDirectory(plugin.getDataFolder().toPath());
        for (String directory : extraDirectories) {
            ensureDirectory(dataPath(directory));
        }
        for (String file : versionedFiles) {
            ensureDefaultFile(file);
            mergeVersionedFile(file);
        }
        for (String file : staticFiles) {
            ensureDefaultFile(file);
        }
        if (hooks.shouldInstallDefaultData()) {
            for (String file : defaultDataFiles) {
                ensureDefaultFile(file);
            }
        } else if (!defaultDataFiles.isEmpty()) {
            info("console.bootstrap_skip");
        }
        hooks.afterBootstrap();
        info("console.bootstrap_complete");
        return true;
    }

    private void ensureDefaultFile(String relativePath) {
        Path target = dataPath(relativePath);
        try {
            byte[] bundled = readBundledResource(relativePath);
            if (bundled == null) {
                warning("console.default_file_missing", Map.of("path", relativePath));
                return;
            }
            if (Files.exists(target)) {
                return;
            }
            ensureDirectory(target.getParent());
            Files.write(target, bundled);
        } catch (IOException exception) {
            warning("console.bootstrap_copy_failed", Map.of(
                    "path", relativePath,
                    "error", String.valueOf(exception.getMessage())
            ));
        }
    }

    private byte[] readBundledResource(String relativePath) throws IOException {
        try (InputStream input = plugin.getResource(relativePath)) {
            return input == null ? null : input.readAllBytes();
        }
    }

    private void mergeVersionedFile(String relativePath) {
        try {
            VersionedYamlFile versionedFile = YamlFiles.syncVersionedResource(
                    plugin,
                    dataPath(relativePath).toFile(),
                    relativePath,
                    VERSION_KEY,
                    document -> hooks.afterVersionedMerge(relativePath, document.root(), document.defaults())
            );
            if (versionedFile == null) {
                warning("console.default_file_missing", Map.of("path", relativePath));
                return;
            }
            logVersionUpdate(relativePath, versionedFile);
        } catch (IOException exception) {
            warning("console.bootstrap_save_failed", Map.of(
                    "path", relativePath,
                    "error", String.valueOf(exception.getMessage())
            ));
        }
    }

    private void logVersionUpdate(String relativePath, VersionedYamlFile versionedFile) {
        if (versionedFile == null || !versionedFile.versionUpdated()) {
            return;
        }
        info("console.versioned_file_updated", Map.of(
                "path", relativePath,
                "old_version", versionedFile.previousVersion().isBlank() ? "unknown" : versionedFile.previousVersion(),
                "new_version", versionedFile.updatedVersion()
        ));
    }

    private void ensureDirectory(Path path) {
        if (path == null) {
            return;
        }
        try {
            YamlFiles.ensureDirectory(path);
        } catch (IOException exception) {
            warning("console.directory_create_failed", Map.of("path", path.toString()));
        }
    }

    private Path dataPath(String relativePath) {
        return plugin.getDataFolder().toPath().resolve(Path.of(relativePath));
    }

    private void info(String key) {
        if (messages != null) {
            messages.info(key);
        }
    }

    private void info(String key, Map<String, ?> replacements) {
        if (messages != null) {
            messages.info(key, replacements);
        }
    }

    private void warning(String key, Map<String, ?> replacements) {
        if (messages != null) {
            messages.warning(key, replacements);
        }
    }

}
