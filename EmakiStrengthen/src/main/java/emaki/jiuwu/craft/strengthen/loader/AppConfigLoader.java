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
            ConfigurationSection economy = configuration.getConfigurationSection("economy");
            ConfigurationSection successRates = configuration.getConfigurationSection("success_rates");
            Map<Integer, Double> rates = new LinkedHashMap<>();
            if (successRates != null) {
                for (String key : successRates.getKeys(false)) {
                    Integer star = Numbers.tryParseInt(key, null);
                    Double value = Numbers.tryParseDouble(successRates.get(key), null);
                    if (star != null && value != null) {
                        rates.put(star, value);
                    }
                }
            }
            AppConfig defaults = AppConfig.defaults();
            current = new AppConfig(
                    configuration.getString("language", defaults.language()),
                    configuration.getString("config_version", defaults.configVersion()),
                    configuration.getBoolean("release_default_data", defaults.releaseDefaultData()),
                    configuration.getString("gui.default_template", defaults.defaultGuiTemplate()),
                    Numbers.tryParseInt(configuration.get("max_star"), defaults.maxStar()),
                    Numbers.tryParseInt(configuration.get("max_crack"), defaults.maxCrack()),
                    Numbers.tryParseDouble(configuration.get("crack_chance_bonus_per_level"), defaults.crackChanceBonusPerLevel()),
                    Numbers.tryParseDouble(configuration.get("success_chance_cap"), defaults.successChanceCap()),
                    Numbers.tryParseInt(configuration.get("local_broadcast_radius"), defaults.localBroadcastRadius()),
                    economy == null ? defaults.economyProvider() : economy.getString("provider", defaults.economyProvider()),
                    economy == null ? defaults.economyCurrencyId() : economy.getString("currency_id", defaults.economyCurrencyId()),
                    economy == null ? defaults.economyCurrencyName() : economy.getString("currency_name", defaults.economyCurrencyName()),
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
}
