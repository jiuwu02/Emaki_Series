package emaki.jiuwu.craft.corelib.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.yaml.VersionedYamlFile;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;

public final class BootstrapService {

    private static final String VERSION_KEY = "version";
    private static final String RESOURCE_STATE_FILE = ".bootstrap-resource-state.properties";

    public enum ResourceStatus {
        INSTALLED,
        CURRENT,
        USER_MODIFIED,
        BUNDLED_UPDATED,
        CONFLICT,
        UNTRACKED,
        FAILED
    }

    public record ResourceEvent(
            String relativePath,
            ResourceStatus status,
            String bundledHash,
            String localHash,
            String backupPath,
            String detail) {

        public ResourceEvent {
            relativePath = relativePath == null ? "" : relativePath;
            status = status == null ? ResourceStatus.FAILED : status;
            bundledHash = bundledHash == null ? "" : bundledHash;
            localHash = localHash == null ? "" : localHash;
            backupPath = backupPath == null ? "" : backupPath;
            detail = detail == null ? "" : detail;
        }
    }

    private final JavaPlugin plugin;
    private final LogMessages messages;
    private final List<String> versionedFiles;
    private final List<String> staticFiles;
    private final List<String> defaultDataFiles;
    private final List<String> extraDirectories;
    private final BootstrapHooks hooks;
    private volatile List<ResourceEvent> resourceEvents = List.of();

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
        Properties resourceState = loadResourceState();
        List<ResourceEvent> events = new ArrayList<>();
        for (String directory : extraDirectories) {
            ensureDirectory(dataPath(directory));
        }
        for (String file : versionedFiles) {
            ensureDefaultFile(file, false, resourceState, events);
            mergeVersionedFile(file);
        }
        for (String file : staticFiles) {
            ensureDefaultFile(file, true, resourceState, events);
        }
        if (hooks.shouldInstallDefaultData()) {
            for (String file : defaultDataFiles) {
                ensureDefaultFile(file, true, resourceState, events);
            }
        } else if (!defaultDataFiles.isEmpty()) {
            info("console.bootstrap_skip");
        }
        saveResourceState(resourceState);
        resourceEvents = List.copyOf(events);
        hooks.afterBootstrap();
        info("console.bootstrap_complete");
        return true;
    }

    public List<ResourceEvent> resourceEvents() {
        return resourceEvents;
    }

    private void ensureDefaultFile(String relativePath,
                                   boolean trackDrift,
                                   Properties resourceState,
                                   List<ResourceEvent> events) {
        Path target = dataPath(relativePath);
        try {
            byte[] bundled = readBundledResource(relativePath);
            if (bundled == null) {
                warning("console.default_file_missing", Map.of("path", relativePath));
                events.add(new ResourceEvent(relativePath, ResourceStatus.FAILED,
                        "", hashFile(target), "", "Bundled resource is missing."));
                return;
            }
            String bundledHash = hashBytes(bundled);
            if (!Files.exists(target)) {
                ensureDirectory(target.getParent());
                Files.write(target, bundled);
                String localHash = hashFile(target);
                updateResourceState(resourceState, relativePath, bundledHash, localHash);
                ResourceEvent event = new ResourceEvent(relativePath, ResourceStatus.INSTALLED,
                        bundledHash, localHash, "", "Installed bundled resource.");
                events.add(event);
                logResourceEvent(event);
                return;
            }
            if (!trackDrift) {
                return;
            }
            String localHash = hashFile(target);
            String previousBundledHash = resourceState.getProperty(resourceStateKey(relativePath, "bundled"), "");
            ResourceEvent event;
            if (localHash.equals(bundledHash)) {
                event = new ResourceEvent(relativePath, ResourceStatus.CURRENT,
                        bundledHash, localHash, "", "Bundled and local resources match.");
            } else if (previousBundledHash.isBlank()) {
                event = new ResourceEvent(relativePath, ResourceStatus.UNTRACKED,
                        bundledHash, localHash, "",
                        "Existing local resource predates drift tracking; preserved without overwrite.");
            } else if (previousBundledHash.equals(bundledHash)) {
                event = new ResourceEvent(relativePath, ResourceStatus.USER_MODIFIED,
                        bundledHash, localHash, "", "Local resource was modified; preserved without overwrite.");
            } else if (localHash.equals(previousBundledHash)) {
                Path backup = backupResource(target);
                Files.write(target, bundled);
                localHash = hashFile(target);
                event = new ResourceEvent(relativePath, ResourceStatus.BUNDLED_UPDATED,
                        bundledHash, localHash, backup.toString(),
                        "Untouched previous bundled resource was backed up and migrated.");
            } else {
                event = new ResourceEvent(relativePath, ResourceStatus.CONFLICT,
                        bundledHash, localHash, "",
                        "Bundled and local resources both changed; local resource was preserved.");
            }
            updateResourceState(resourceState, relativePath, bundledHash, localHash);
            events.add(event);
            logResourceEvent(event);
        } catch (IOException exception) {
            warning("console.bootstrap_copy_failed", Map.of(
                    "path", relativePath,
                    "error", String.valueOf(exception.getMessage())
            ));
            events.add(new ResourceEvent(relativePath, ResourceStatus.FAILED,
                    "", hashFile(target), "", String.valueOf(exception.getMessage())));
        }
    }

    private Properties loadResourceState() {
        Properties state = new Properties();
        Path path = dataPath(RESOURCE_STATE_FILE);
        if (!Files.isRegularFile(path)) {
            return state;
        }
        try (InputStream input = Files.newInputStream(path)) {
            state.load(input);
        } catch (IOException exception) {
            plugin.getLogger().warning("[Bootstrap] Failed to read resource state " + path
                    + ": " + String.valueOf(exception.getMessage()));
        }
        return state;
    }

    private void saveResourceState(Properties state) {
        if (state == null) {
            return;
        }
        Path path = dataPath(RESOURCE_STATE_FILE);
        try {
            ensureDirectory(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                state.store(output, "Emaki bundled resource drift state");
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("[Bootstrap] Failed to save resource state " + path
                    + ": " + String.valueOf(exception.getMessage()));
        }
    }

    private byte[] readBundledResource(String relativePath) throws IOException {
        try (InputStream input = plugin.getResource(relativePath)) {
            return input == null ? null : input.readAllBytes();
        }
    }

    private Path backupResource(Path target) throws IOException {
        String backupName = target.getFileName() + ".bak." + System.currentTimeMillis();
        Path backup = target.resolveSibling(backupName);
        Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        return backup;
    }

    private void updateResourceState(Properties state,
                                     String relativePath,
                                     String bundledHash,
                                     String localHash) {
        if (state == null) {
            return;
        }
        state.setProperty(resourceStateKey(relativePath, "bundled"), bundledHash == null ? "" : bundledHash);
        state.setProperty(resourceStateKey(relativePath, "local"), localHash == null ? "" : localHash);
    }

    private String resourceStateKey(String relativePath, String suffix) {
        String normalized = relativePath == null ? "" : relativePath.replace('\\', '/');
        return "resource." + normalized + "." + suffix;
    }

    private String hashFile(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return "";
        }
        try {
            return hashBytes(Files.readAllBytes(path));
        } catch (IOException exception) {
            return "";
        }
    }

    private String hashBytes(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void logResourceEvent(ResourceEvent event) {
        if (event == null || event.status() == ResourceStatus.CURRENT) {
            return;
        }
        String message = "[Bootstrap][Resource] status=" + event.status()
                + " path=" + event.relativePath()
                + (event.backupPath().isBlank() ? "" : " backup=" + event.backupPath())
                + (event.detail().isBlank() ? "" : " detail=" + event.detail());
        switch (event.status()) {
            case INSTALLED, BUNDLED_UPDATED -> plugin.getLogger().info(message);
            case CONFLICT, FAILED -> plugin.getLogger().warning(message);
            case CURRENT, USER_MODIFIED, UNTRACKED -> {
            }
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
