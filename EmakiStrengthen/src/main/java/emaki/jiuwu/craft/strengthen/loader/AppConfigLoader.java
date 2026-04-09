package emaki.jiuwu.craft.strengthen.loader;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.config.AppConfig;

public final class AppConfigLoader {

    private final EmakiStrengthenPlugin plugin;
    private AppConfig current = AppConfig.defaults();

    public AppConfigLoader(EmakiStrengthenPlugin plugin) {
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
            Map<Integer, Double> rates = parseSuccessRates(configuration.getConfigurationSection("success_rates"));
            AppConfig defaults = AppConfig.defaults();
            current = new AppConfig(
                    configuration.getString("language", defaults.language()),
                    configuration.getString("config_version", defaults.configVersion()),
                    Numbers.tryParseInt(configuration.get("local_broadcast_radius"), defaults.localBroadcastRadius()),
                    rates.isEmpty() ? defaults.successRates() : rates
            );
        } catch (Exception exception) {
            if (plugin.messageService() != null) {
                plugin.messageService().warning("console.loader_config_load_error", Map.of(
                        "path", "config.yml",
                        "error", String.valueOf(exception.getMessage())
                ));
            }
            current = AppConfig.defaults();
        }
        return current;
    }

    public AppConfig current() {
        return current;
    }

    private Map<Integer, Double> parseSuccessRates(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<Integer, Double> rates = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            Integer star = Numbers.tryParseInt(key, null);
            Double value = Numbers.tryParseDouble(section.get(key), null);
            if (star != null && value != null) {
                rates.put(star, value);
            }
        }
        return rates;
    }
}
