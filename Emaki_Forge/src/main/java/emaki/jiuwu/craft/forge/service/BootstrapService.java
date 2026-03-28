package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

public final class BootstrapService {

    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml");
    private static final List<String> STATIC_FILES = List.of("gui/forge_gui.yml", "gui/recipe_book.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of(
        "blueprints/universal_blueprint.yml",
        "blueprints/weapon_sword.yml",
        "materials/forge_capacity_core.yml",
        "materials/chi_yan_jing.yml",
        "materials/flame_crystal.yml",
        "materials/iron_essence.yml",
        "materials/qing_feng_mu.yml",
        "materials/xuan_tie_core.yml",
        "materials/yun_lei_sha.yml",
        "recipes/flame_sword.yml"
    );

    private final EmakiForgePlugin plugin;
    private final MessageService messages;

    public BootstrapService(EmakiForgePlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public boolean bootstrap() {
        messages.info("console.bootstrap_start");
        ensureDirectory(plugin.getDataFolder().toPath());
        migrateLegacyDefaultData();
        importLegacyPySpigotDataIfPresent();
        for (String file : VERSIONED_FILES) {
            ensureDefaultFile(file);
            mergeVersionedFile(file);
        }
        for (String file : STATIC_FILES) {
            ensureDefaultFile(file);
        }
        if (shouldReleaseDefaultData()) {
            for (String file : DEFAULT_DATA_FILES) {
                ensureDefaultFile(file);
            }
        } else {
            messages.info("console.bootstrap_skip");
        }
        ensureDirectory(plugin.dataPath("data"));
        messages.info("console.bootstrap_complete");
        return true;
    }

    private void importLegacyPySpigotDataIfPresent() {
        Path targetRoot = plugin.getDataFolder().toPath();
        if (Files.exists(targetRoot.resolve("config.yml"))) {
            return;
        }
        Path pluginsDir = targetRoot.getParent();
        if (pluginsDir == null) {
            return;
        }
        Path legacyRoot = pluginsDir.resolve("PySpigot").resolve("configs").resolve("JiuWus_Forge");
        if (!Files.exists(legacyRoot)) {
            return;
        }
        try (var paths = Files.walk(legacyRoot)) {
            paths.forEach(source -> {
                try {
                    Path relative = legacyRoot.relativize(source);
                    Path target = targetRoot.resolve(relative);
                    if (Files.isDirectory(source)) {
                        ensureDirectory(target);
                        return;
                    }
                    if (!Files.exists(target)) {
                        ensureDirectory(target.getParent());
                        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException exception) {
                    messages.warning("console.bootstrap_import_legacy_failed", Map.of(
                        "path", source.toString(),
                        "error", String.valueOf(exception.getMessage())
                    ));
                }
            });
        } catch (IOException exception) {
            messages.warning("console.bootstrap_scan_legacy_failed", Map.of(
                "path", legacyRoot.toString(),
                "error", String.valueOf(exception.getMessage())
            ));
        }
    }

    private void ensureDefaultFile(String relativePath) {
        Path target = plugin.dataPath(relativePath);
        if (Files.exists(target)) {
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

    private void mergeVersionedFile(String relativePath) {
        YamlConfiguration runtime = YamlFiles.load(plugin.dataPath(relativePath).toFile());
        YamlConfiguration bundled = YamlFiles.loadResource(plugin, relativePath);
        if (bundled == null) {
            return;
        }
        String versionKey = relativePath.startsWith("lang/") ? "lang_version" : "config_version";
        String currentVersion = runtime.getString(versionKey);
        String bundledVersion = bundled.getString(versionKey);
        if (bundledVersion == null) {
            return;
        }
        if (currentVersion != null && compareVersions(currentVersion, bundledVersion) >= 0) {
            return;
        }
        YamlFiles.mergeMissingValues(runtime, bundled);
        applyVersionMigrations(relativePath, runtime);
        runtime.set(versionKey, bundledVersion);
        try {
            YamlFiles.save(plugin.dataPath(relativePath).toFile(), runtime);
        } catch (IOException exception) {
            messages.warning("console.bootstrap_save_failed", Map.of(
                "path", relativePath,
                "error", String.valueOf(exception.getMessage())
            ));
        }
    }

    private boolean shouldReleaseDefaultData() {
        YamlConfiguration configuration = YamlFiles.load(plugin.dataPath("config.yml").toFile());
        return configuration.getBoolean("release_default_data", true);
    }

    private void migrateLegacyDefaultData() {
        Path legacyRoot = plugin.getDataFolder().toPath().resolve("defaults");
        if (!Files.exists(legacyRoot) || !Files.isDirectory(legacyRoot)) {
            return;
        }
        try (var paths = Files.walk(legacyRoot)) {
            paths.forEach(source -> {
                try {
                    Path relative = legacyRoot.relativize(source);
                    if (relative.toString().isBlank()) {
                        return;
                    }
                    Path target = plugin.getDataFolder().toPath().resolve(relative);
                    if (Files.isDirectory(source)) {
                        ensureDirectory(target);
                        return;
                    }
                    if (Files.exists(target)) {
                        return;
                    }
                    ensureDirectory(target.getParent());
                    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException exception) {
                    messages.warning("console.bootstrap_migrate_legacy_failed", Map.of(
                        "path", source.toString(),
                        "error", String.valueOf(exception.getMessage())
                    ));
                }
            });
        } catch (IOException exception) {
            messages.warning("console.bootstrap_scan_legacy_default_failed", Map.of(
                "path", legacyRoot.toString(),
                "error", String.valueOf(exception.getMessage())
            ));
        }
    }

    private void replaceLangValue(YamlConfiguration runtime, String path, String expected, String replacement) {
        String current = runtime.getString(path);
        if (expected.equals(current)) {
            runtime.set(path, replacement);
        }
    }

    private int compareVersions(String current, String latest) {
        if (current == null) {
            return -1;
        }
        try {
            String[] currentParts = current.split("\\.");
            String[] latestParts = latest.split("\\.");
            int length = Math.max(currentParts.length, latestParts.length);
            for (int index = 0; index < length; index++) {
                int currentValue = index < currentParts.length ? Integer.parseInt(currentParts[index]) : 0;
                int latestValue = index < latestParts.length ? Integer.parseInt(latestParts[index]) : 0;
                if (currentValue != latestValue) {
                    return Integer.compare(currentValue, latestValue);
                }
            }
        } catch (Exception ignored) {
            return 0;
        }
        return 0;
    }

    private void applyVersionMigrations(String relativePath, YamlConfiguration runtime) {
        if (!relativePath.startsWith("lang/")) {
            return;
        }
        for (String key : runtime.getKeys(true)) {
            if (!runtime.isString(key)) {
                continue;
            }
            String value = runtime.getString(key);
            if (value != null && value.contains("JiuWu's Forge")) {
                runtime.set(key, value.replace("JiuWu's Forge", "Emaki Forge"));
            }
        }
        replaceLangValue(runtime, "console.plugin_starting", "<white>Emaki Forge <gray>正在启动...</gray>", "<gray>正在启动...</gray>");
        replaceLangValue(runtime, "console.plugin_started", "<green>Emaki Forge <gray>启动完成!</gray>", "<green>启动完成!</green>");
        replaceLangValue(runtime, "console.plugin_stopping", "<white>Emaki Forge <gray>正在关闭...</gray>", "<gray>正在关闭...</gray>");
        replaceLangValue(runtime, "console.plugin_stopped", "<green>Emaki Forge <gray>已关闭!</gray>", "<green>已关闭!</green>");
    }


    private void ensureDirectory(Path path) {
        if (path == null) {
            return;
        }
        try {
            YamlFiles.ensureDirectory(path);
        } catch (IOException exception) {
            messages.warning("console.directory_create_failed", Map.of(
                "path", path.toString()
            ));
        }
    }
}
