package emaki.jiuwu.craft.strengthen.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;

public final class BootstrapService {

    private static final List<String> VERSIONED_FILES = List.of("config.yml", "lang/zh_CN.yml");
    private static final List<String> STATIC_FILES = List.of("gui/strengthen_gui.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of(
            "profiles/weapon_physical.yml",
            "profiles/weapon_spell.yml",
            "profiles/weapon_projectile.yml",
            "profiles/armor_guard.yml",
            "profiles/offhand_focus.yml",
            "profiles/generic_visual.yml",
            "rules/armor_default.yml",
            "rules/offhand_default.yml",
            "rules/projectile_keyword.yml",
            "rules/spell_keyword.yml",
            "materials/base_catalyst.yml",
            "materials/support_catalyst.yml",
            "materials/protection_catalyst.yml",
            "materials/breakthrough_catalyst_basic.yml",
            "materials/breakthrough_catalyst_advanced.yml",
            "materials/cleanse_catalyst.yml"
    );

    private final EmakiStrengthenPlugin plugin;
    private final MessageService messages;

    public BootstrapService(EmakiStrengthenPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void bootstrap() {
        messages.info("console.bootstrap_start");
        ensureDirectory(plugin.getDataFolder().toPath());
        for (String file : VERSIONED_FILES) {
            ensureDefaultFile(file);
            mergeVersioned(file);
        }
        for (String file : STATIC_FILES) {
            ensureDefaultFile(file);
        }
        if (plugin.appConfig() == null || plugin.appConfig().releaseDefaultData()) {
            for (String file : DEFAULT_DATA_FILES) {
                ensureDefaultFile(file);
            }
        }
        messages.info("console.bootstrap_complete");
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
