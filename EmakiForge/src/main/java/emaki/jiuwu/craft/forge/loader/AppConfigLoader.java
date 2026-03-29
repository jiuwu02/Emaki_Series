package emaki.jiuwu.craft.forge.loader;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.model.QualitySettings;
import java.io.IOException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class AppConfigLoader {

    private final EmakiForgePlugin plugin;
    private AppConfig current = AppConfig.defaults();

    public AppConfigLoader(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    public AppConfig load() {
        try {
            java.io.File file = plugin.dataPath("config.yml").toFile();
            YamlFiles.syncVersionedResource(plugin, file, "config.yml", "config_version");
            YamlConfiguration configuration = YamlFiles.load(file);
            if (configuration.getKeys(false).isEmpty()) {
                current = AppConfig.defaults();
                return current;
            }
            ConfigurationSection permission = configuration.getConfigurationSection("permission");
            ConfigurationSection condition = configuration.getConfigurationSection("condition");
            ConfigurationSection history = configuration.getConfigurationSection("history");
            ConfigurationSection numberFormat = configuration.getConfigurationSection("number_format");
            current = new AppConfig(
                configuration.getString("language", "zh_CN"),
                configuration.getString("config_version", "1.1"),
                configuration.getBoolean("release_default_data", true),
                QualitySettings.fromConfig(configuration.get("quality")),
                numberFormat == null ? "0.##" : numberFormat.getString("default", "0.##"),
                numberFormat == null ? "0" : numberFormat.getString("integer", "0"),
                numberFormat == null ? "0.##%" : numberFormat.getString("percentage", "0.##%"),
                permission != null && permission.getBoolean("op_bypass", false),
                condition == null || condition.getBoolean("invalid_as_failure", true),
                history == null || history.getBoolean("enabled", true),
                history == null || history.getBoolean("auto_save", true),
                history == null ? 6000 : Numbers.tryParseInt(history.get("save_interval"), 6000)
            );
        } catch (Exception exception) {
            plugin.messageService().warning("console.loader_config_load_error", java.util.Map.of(
                "path", "config.yml",
                "error", String.valueOf(exception.getMessage())
            ));
            current = AppConfig.defaults();
        }
        return current;
    }

    public AppConfig current() {
        return current;
    }

    public void overrideCurrent(AppConfig current) {
        this.current = current;
    }
}
